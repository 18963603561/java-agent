package com.example.agent.governance.replay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 回放治理配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.replay")
public class ReplayProperties {

    /**
     * 会话缓存保留时长（秒）。
     */
    private int sessionTtlSeconds = 1800;

    /**
     * 会话缓存最大数量。
     */
    private int sessionMaxSize = 2000;

    /**
     * 会话清理周期（秒）。
     */
    private int cleanupIntervalSeconds = 30;

    public int getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(int sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public int getSessionMaxSize() {
        return sessionMaxSize;
    }

    public void setSessionMaxSize(int sessionMaxSize) {
        this.sessionMaxSize = sessionMaxSize;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }
}

