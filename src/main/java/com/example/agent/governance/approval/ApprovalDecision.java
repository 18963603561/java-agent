package com.example.agent.governance.approval;

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

    /**
     * 构建审批通过的结果。
     *
     * @param requestId 审批请求标识
     * @param reason 通过原因或备注
     * @return 审批通过结果
     */
    public static ApprovalDecision approved(String requestId, String reason) {
        return new ApprovalDecision(true, false, reason, requestId, System.currentTimeMillis());
    }

    /**
     * 构建审批拒绝的结果。
     *
     * @param requestId 审批请求标识
     * @param reason 拒绝原因或备注
     * @return 审批拒绝结果
     */
    public static ApprovalDecision rejected(String requestId, String reason) {
        return new ApprovalDecision(false, false, reason, requestId, System.currentTimeMillis());
    }

    /**
     * 构建审批超时的结果。
     *
     * @param requestId 审批请求标识
     * @param reason 超时原因或备注
     * @return 审批超时结果
     */
    public static ApprovalDecision timeout(String requestId, String reason) {
        return new ApprovalDecision(false, true, reason, requestId, System.currentTimeMillis());
    }

    /**
     * 获取是否审批通过。
     *
     * @return 是否审批通过
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * 获取是否审批超时。
     *
     * @return 是否审批超时
     */
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
