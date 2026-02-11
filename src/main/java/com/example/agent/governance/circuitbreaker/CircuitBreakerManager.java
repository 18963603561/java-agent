package com.example.agent.governance.circuitbreaker;

import com.example.agent.governance.circuitbreaker.domain.CircuitStateEntry;
import com.example.agent.governance.circuitbreaker.domain.CircuitDecision;
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

    @Autowired
    public CircuitBreakerManager(MetricsPublisher metricsPublisher,
                                 CircuitStateStore circuitStateStore,
                                 GovernanceTelemetry governanceTelemetry) {
        this.metricsPublisher = metricsPublisher;
        this.circuitStateStore = circuitStateStore;
        this.governanceTelemetry = governanceTelemetry;
    }

    public boolean allow(String key) {
        return evaluate(key).isAllowed();
    }

    /**
     * 执行熔断判定并返回结构化结果。
     *
     * @param key 熔断键
     * @return 熔断决策
     */
    public CircuitDecision evaluate(String key) {
        StateStorePolicy policy = resolveStorePolicy();
        circuitStateStore.cleanup(policy);
        if (key == null || key.isBlank()) {
            log.warn("熔断键缺失，默认拒绝请求");
            metricsPublisher.incrementWithTags("governance.circuit.rejected_total", "reason", "blank_key");
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "rejected_blank_key");
            return new CircuitDecision(false, "blank_key", "invalid", 0L, openSeconds);
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
                return new CircuitDecision(false, "capacity", "capacity_rejected", 0L, openSeconds);
            }
            state.touch();
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "allowed");
            return new CircuitDecision(true, "allowed", "closed", state.getOpenedAtEpochSeconds(), openSeconds);
        }
        state.touch();
        if (!state.isOpen()) {
            governanceTelemetry.increment("circuit.allow.total",
                    "domain", "circuit",
                    "action", "allow",
                    "result", "allowed");
            return new CircuitDecision(true, "allowed", "closed", state.getOpenedAtEpochSeconds(), openSeconds);
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
            return new CircuitDecision(true, "recover_open_to_closed", "closed", state.getOpenedAtEpochSeconds(), openSeconds);
        }
        metricsPublisher.incrementWithTags("governance.circuit.rejected_total", "reason", "open_state");
        governanceTelemetry.increment("circuit.allow.total",
                "domain", "circuit",
                "action", "allow",
                "result", "rejected_open_state");
        return new CircuitDecision(false, "open_state", "open", state.getOpenedAtEpochSeconds(), openSeconds);
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
