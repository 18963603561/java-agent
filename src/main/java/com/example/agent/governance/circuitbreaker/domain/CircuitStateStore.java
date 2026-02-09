package com.example.agent.governance.circuitbreaker.domain;

import com.example.agent.governance.common.state.GenericStateStore;
import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.state.StoreMetricsRecorder;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 熔断状态仓储。
 */
@Component
public class CircuitStateStore {

    private static final Logger log = LoggerFactory.getLogger(CircuitStateStore.class);

    private final GenericStateStore<CircuitStateEntry> stateStore;

    public CircuitStateStore(MetricsPublisher metricsPublisher) {
        this.stateStore = new GenericStateStore<>(
                CircuitStateEntry::getLastAccessEpochMs,
                (key, entry) -> {
                    // 过期清理无需额外回调。
                },
                new StoreMetricsRecorder<>() {
                    @Override
                    public void onCapacityRejected(CircuitStateEntry value, int currentSize, int maxSize) {
                        metricsPublisher.incrementWithTags("governance.circuit.rejected_total", "reason", "capacity");
                        log.warn("熔断缓存容量超限, currentSize={}, maxKeys={}", currentSize, maxSize);
                    }

                    @Override
                    public void onCleanup(int removed, int currentSize) {
                        for (int index = 0; index < removed; index++) {
                            metricsPublisher.incrementWithTags("governance.circuit.cleanup_total", "cause", "expired");
                        }
                        metricsPublisher.recordSummary("governance.circuit.cleanup_removed", removed);
                        log.info("熔断状态清理完成, removed={}, currentSize={}", removed, currentSize);
                    }
                });
    }

    /**
     * 读取状态记录。
     *
     * @param key 熔断键
     * @param policy 仓储策略
     * @return 状态记录
     */
    public CircuitStateEntry get(String key, StateStorePolicy policy) {
        return stateStore.get(key, policy);
    }

    /**
     * 写入状态记录。
     *
     * @param key 熔断键
     * @param entry 状态记录
     * @param policy 仓储策略
     * @return 是否写入成功
     */
    public boolean put(String key, CircuitStateEntry entry, StateStorePolicy policy) {
        return stateStore.put(key, entry, policy);
    }

    /**
     * 读取或原子创建状态记录。
     *
     * @param key 熔断键
     * @param policy 仓储策略
     * @return 状态记录，容量超限时返回空
     */
    public CircuitStateEntry getOrCreate(String key, StateStorePolicy policy) {
        return stateStore.getOrCreate(key, policy, CircuitStateEntry::new);
    }

    /**
     * 获取状态数量。
     *
     * @param policy 仓储策略
     * @return 数量
     */
    public int size(StateStorePolicy policy) {
        return stateStore.size(policy);
    }

    /**
     * 执行惰性清理。
     *
     * @param policy 仓储策略
     */
    public void cleanup(StateStorePolicy policy) {
        stateStore.cleanupIfNecessary(policy);
    }
}
