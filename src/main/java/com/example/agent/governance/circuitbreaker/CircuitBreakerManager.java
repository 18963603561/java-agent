package com.example.agent.governance.circuitbreaker;

import com.example.agent.governance.circuitbreaker.domain.CircuitStateEntry;
import com.example.agent.governance.circuitbreaker.domain.CircuitStateStore;
import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 简易熔断管理器，按失败次数触发熔断。
 */
@Service
public class CircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManager.class);

    private final MetricsPublisher metricsPublisher;
    private final CircuitStateStore circuitStateStore;
    private final GovernanceTelemetry governanceTelemetry;

    @Value("${agent.circuit.failure-threshold:3}")
    private int failureThreshold;

    @Value("${agent.circuit.open-seconds:30}")
    private int openSeconds;

    @Value("${agent.circuit.max-keys:20000}")
    private int maxKeys;

    @Value("${agent.circuit.state-ttl-seconds:3600}")
    private int stateTtlSeconds;

    @Value("${agent.circuit.cleanup-interval-seconds:30}")
    private int cleanupIntervalSeconds;

    public CircuitBreakerManager(MetricsPublisher metricsPublisher, CircuitStateStore circuitStateStore) {
        this(metricsPublisher, circuitStateStore, new GovernanceTelemetry(metricsPublisher));
    }

    @Autowired
    public CircuitBreakerManager(MetricsPublisher metricsPublisher,
                                 CircuitStateStore circuitStateStore,
                                 GovernanceTelemetry governanceTelemetry) {
        this.metricsPublisher = metricsPublisher;
        this.circuitStateStore = circuitStateStore;
        this.governanceTelemetry = governanceTelemetry;
    }

    public boolean allow(String key) {
        StateStorePolicy policy = resolveStorePolicy();
        circuitStateStore.cleanup(policy);
        if (key == null || key.isBlank()) {
            log.warn("熔断键缺失，默认拒绝请求");
            metricsPublisher.incrementWithTags("governance.circuit.rejected_total", "reason", "blank_key");
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "rejected_blank_key");
            return false;
        }
        CircuitStateEntry state = circuitStateStore.get(key, policy);
        if (state == null) {
            state = circuitStateStore.getOrCreate(key, policy);
            if (state == null) {
                log.warn("熔断缓存容量超限, key={}, currentSize={}, maxKeys={}", key, circuitStateStore.size(policy), maxKeys);
                governanceTelemetry.increment("circuit.allow.total",
                        "domain", "circuit",
                        "action", "allow",
                        "result", "rejected_capacity");
                return false;
            }
            state.touch();
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "allowed");
            return true;
        }
        state.touch();
        if (!state.isOpen()) {
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "allowed");
            return true;
        }
        long now = Instant.now().getEpochSecond();
        boolean allow = now - state.getOpenedAtEpochSeconds() >= openSeconds;
        if (allow) {
            state.closeAndReset();
            metricsPublisher.incrementWithTags("governance.circuit.state_change_total",
                    "from", "open", "to", "closed");
            log.info("熔断恢复, key={}, openSeconds={}", key, openSeconds);
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "recover_open_to_closed");
            return true;
        }
        metricsPublisher.incrementWithTags("governance.circuit.rejected_total", "reason", "open_state");
        governanceTelemetry.increment("circuit.allow.total",
                "domain", "circuit",
                "action", "allow",
                "result", "rejected_open_state");
        return false;
    }

    public void recordFailure(String key) {
        StateStorePolicy policy = resolveStorePolicy();
        circuitStateStore.cleanup(policy);
        if (key == null || key.isBlank()) {
            return;
        }
        CircuitStateEntry state = circuitStateStore.get(key, policy);
        if (state == null) {
            state = circuitStateStore.getOrCreate(key, policy);
            if (state == null) {
                log.warn("熔断缓存容量超限，忽略失败记录, key={}, currentSize={}, maxKeys={}",
                        key, circuitStateStore.size(policy), maxKeys);
                governanceTelemetry.increment("circuit.record_failure.total",
                        "domain", "circuit",
                        "action", "record_failure",
                        "result", "rejected_capacity");
                return;
            }
        }
        int failures = state.getFailures().incrementAndGet();
        state.touch();
        if (failures >= failureThreshold) {
            boolean wasOpen = state.isOpen();
            state.openNow();
            if (!wasOpen) {
                metricsPublisher.incrementWithTags("governance.circuit.state_change_total",
                        "from", "closed", "to", "open");
                log.warn("熔断开启, key={}, failures={}, threshold={}", key, failures, failureThreshold);
                governanceTelemetry.increment("circuit.state_change.total",
                        "domain", "circuit",
                        "action", "record_failure",
                        "result", "closed_to_open");
            }
        }
    }

    public void recordSuccess(String key) {
        StateStorePolicy policy = resolveStorePolicy();
        circuitStateStore.cleanup(policy);
        if (key == null || key.isBlank()) {
            return;
        }
        CircuitStateEntry state = circuitStateStore.get(key, policy);
        if (state == null) {
            state = circuitStateStore.getOrCreate(key, policy);
            if (state == null) {
                return;
            }
        }
        boolean wasOpen = state.isOpen();
        state.closeAndReset();
        if (wasOpen) {
            metricsPublisher.incrementWithTags("governance.circuit.state_change_total",
                    "from", "open", "to", "closed");
            log.info("熔断恢复, key={}, reason=record_success", key);
            governanceTelemetry.increment("circuit.state_change.total",
                    "domain", "circuit",
                    "action", "record_success",
                    "result", "open_to_closed");
        }
    }

    /**
     * 获取缓存状态数量，仅用于测试和诊断。
     *
     * @return 状态数量
     */
    public int stateSize() {
        StateStorePolicy policy = resolveStorePolicy();
        circuitStateStore.cleanup(policy);
        return circuitStateStore.size(policy);
    }

    private StateStorePolicy resolveStorePolicy() {
        return new StateStorePolicy(stateTtlSeconds, maxKeys, cleanupIntervalSeconds);
    }
}
