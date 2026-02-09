package com.example.agent.governance.replay.domain;

/**
 * 回放会话存储配置。
 */
public class ReplaySessionStoreProperties {

    private final int sessionTtlSeconds;
    private final int sessionMaxSize;
    private final int cleanupIntervalSeconds;

    public ReplaySessionStoreProperties(int sessionTtlSeconds, int sessionMaxSize, int cleanupIntervalSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.sessionMaxSize = sessionMaxSize;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    public int getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public int getSessionMaxSize() {
        return sessionMaxSize;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }
}

