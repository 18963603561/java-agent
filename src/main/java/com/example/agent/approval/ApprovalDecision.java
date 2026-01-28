package com.example.agent.approval;

/**
 * 审批决策结果。
 */
public class ApprovalDecision {

    /**
     * 是否通过审批。
     */
    private final boolean approved;

    /**
     * 是否超时。
     */
    private final boolean timeout;

    /**
     * 决策原因。
     */
    private final String reason;

    /**
     * 审批请求标识。
     */
    private final String requestId;

    /**
     * 决策时间戳（毫秒）。
     */
    private final long decidedAtEpochMs;

    private ApprovalDecision(boolean approved, boolean timeout, String reason, String requestId, long decidedAtEpochMs) {
        this.approved = approved;
        this.timeout = timeout;
        this.reason = reason;
        this.requestId = requestId;
        this.decidedAtEpochMs = decidedAtEpochMs;
    }

    public static ApprovalDecision approved(String requestId, String reason) {
        return new ApprovalDecision(true, false, reason, requestId, System.currentTimeMillis());
    }

    public static ApprovalDecision rejected(String requestId, String reason) {
        return new ApprovalDecision(false, false, reason, requestId, System.currentTimeMillis());
    }

    public static ApprovalDecision timeout(String requestId, String reason) {
        return new ApprovalDecision(false, true, reason, requestId, System.currentTimeMillis());
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public String getReason() {
        return reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getDecidedAtEpochMs() {
        return decidedAtEpochMs;
    }
}
