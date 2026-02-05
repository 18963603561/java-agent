package com.example.agent.governance.approval;

import java.util.concurrent.CompletableFuture;

/**
 * 审批句柄，携带请求标识与等待结果的 Future。
 */
public class ApprovalHandle {

    /**
     * 审批请求标识。
     */
    private final String requestId;

    /**
     * 决策等待句柄。
     */
    private final CompletableFuture<ApprovalDecision> future;

    /**
     * 请求创建时间戳（毫秒）。
     */
    private final long createdAtEpochMs;

    public ApprovalHandle(String requestId,
                          CompletableFuture<ApprovalDecision> future,
                          long createdAtEpochMs) {
        this.requestId = requestId;
        this.future = future;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getRequestId() {
        return requestId;
    }

    public CompletableFuture<ApprovalDecision> getFuture() {
        return future;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }
}
