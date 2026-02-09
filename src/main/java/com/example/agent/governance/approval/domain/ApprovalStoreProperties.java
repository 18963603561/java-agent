package com.example.agent.governance.approval.domain;

/**
 * 待审批存储配置。
 */
public class ApprovalStoreProperties {

    private final int pendingTtlSeconds;
    private final int pendingMaxSize;
    private final int cleanupIntervalSeconds;

    public ApprovalStoreProperties(int pendingTtlSeconds, int pendingMaxSize, int cleanupIntervalSeconds) {
        this.pendingTtlSeconds = pendingTtlSeconds;
        this.pendingMaxSize = pendingMaxSize;
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    public int getPendingTtlSeconds() {
        return pendingTtlSeconds;
    }

    public int getPendingMaxSize() {
        return pendingMaxSize;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }
}

