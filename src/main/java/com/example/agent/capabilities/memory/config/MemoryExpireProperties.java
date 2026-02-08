package com.example.agent.capabilities.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆过期配置，用于控制 TTL 与清理策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.memory.expire")
public class MemoryExpireProperties {

    /**
     * 是否启用过期控制。
     */
    private boolean enabled = true;

    /**
     * 默认过期秒数。
     */
    private long ttlSeconds = 2592000;

    /**
     * 是否在查询时触发清理。
     */
    private boolean cleanupOnRead = true;

    /**
     * 清理最小间隔秒数。
     */
    private long cleanupIntervalSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isCleanupOnRead() {
        return cleanupOnRead;
    }

    public void setCleanupOnRead(boolean cleanupOnRead) {
        this.cleanupOnRead = cleanupOnRead;
    }

    public long getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}
