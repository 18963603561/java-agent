package com.example.agent.governance.ratelimit.domain;

import com.example.agent.governance.common.state.GenericStateStore;
import com.example.agent.governance.common.state.StateStorePolicy;
import com.example.agent.governance.common.state.StoreMetricsRecorder;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 限流状态仓储。
 */
@Component
public class RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RateLimitStore.class);

    private final GenericStateStore<RateLimitWindowEntry> stateStore;

    public RateLimitStore(MetricsPublisher metricsPublisher) {
        this.stateStore = new GenericStateStore<>(
                RateLimitWindowEntry::getLastAccessEpochMs,
                (key, entry) -> {
                    // 过期后不需要额外回调。
                },
                new StoreMetricsRecorder<>() {
                    @Override
                    public void onCapacityRejected(RateLimitWindowEntry value, int currentSize, int maxSize) {
                        metricsPublisher.incrementWithTags("governance.ratelimit.rejected_total", "reason", "capacity");
                        log.warn("限流缓存容量超限, currentSize={}, maxKeys={}", currentSize, maxSize);
                    }

                    @Override
                    public void onCleanup(int removed, int currentSize) {
                        for (int index = 0; index < removed; index++) {
                            metricsPublisher.incrementWithTags("governance.ratelimit.cleanup_total", "cause", "expired");
                        }
                        metricsPublisher.recordSummary("governance.ratelimit.cleanup_removed", removed);
                        log.info("限流缓存清理完成, removed={}, currentSize={}", removed, currentSize);
                    }
                });
    }

    /**
     * 读取限流记录。
     *
     * @param key 限流键
     * @param policy 仓储策略
     * @return 记录
     */
    public RateLimitWindowEntry get(String key, StateStorePolicy policy) {
        return stateStore.get(key, policy);
    }

    /**
     * 写入限流记录。
     *
     * @param key 限流键
     * @param entry 记录
     * @param policy 仓储策略
     * @return 是否写入成功
     */
    public boolean put(String key, RateLimitWindowEntry entry, StateStorePolicy policy) {
        return stateStore.put(key, entry, policy);
    }

    /**
     * 读取或原子创建限流记录。
     *
     * @param key 限流键
     * @param currentWindow 当前分钟窗口
     * @param policy 仓储策略
     * @return 限流记录，容量超限时返回空
     */
    public RateLimitWindowEntry getOrCreate(String key, long currentWindow, StateStorePolicy policy) {
        return stateStore.getOrCreate(key, policy, () -> new RateLimitWindowEntry(currentWindow));
    }

    /**
     * 获取缓存键数量。
     *
     * @param policy 仓储策略
     * @return 键数量
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
