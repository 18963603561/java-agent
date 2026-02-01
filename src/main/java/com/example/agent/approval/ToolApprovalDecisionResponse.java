package com.example.agent.approval;

/**
 * 工具审批决策响应。
 */
public class ToolApprovalDecisionResponse {

    /**
     * 审批请求标识。
     */
    private String requestId;

    /**
     * 是否通过审批。
     */
    private boolean approved;

    /**
     * 决策原因。
     */
    private String reason;

    public ToolApprovalDecisionResponse() {
    }

    public ToolApprovalDecisionResponse(String requestId, boolean approved, String reason) {
        this.requestId = requestId;
        this.approved = approved;
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * 获取是否审批通过。
     *
     * @return 是否审批通过
     */
    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
