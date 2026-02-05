package com.example.agent.governance.approval;

/**
 * 工具审批决策请求。
 */
public class ToolApprovalDecisionRequest {

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 审批请求标识。
     */
    private String requestId;

    /**
     * 是否通过审批。
     */
    private Boolean approved;

    /**
     * 决策原因。
     */
    private String reason;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
