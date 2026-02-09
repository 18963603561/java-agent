package com.example.agent.governance.common.state;

/**
 * 状态仓储策略，统一定义生命周期参数。
 */
public class StateStorePolicy {

    /**
     * 记录空闲过期时间（秒）。
     */
    private final int ttlSeconds;

    /**
     * 最大容量。
     */
    private final int maxSize;

    /**
     * 清理触发周期（秒）。
     */
    private final int cleanupIntervalSeconds;

    public StateStorePolicy(int ttlSeconds, int maxSize, int cleanupIntervalSeconds) {
        this.ttlSeconds = Math.max(1, ttlSeconds);
        this.maxSize = Math.max(1, maxSize);
        this.cleanupIntervalSeconds = Math.max(1, cleanupIntervalSeconds);
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }
}

