package com.example.agent.runtime.model.plan;

/**
 * 步骤策略配置。
 *
 * <p>用途：描述步骤执行过程中的审批策略。
 */
public class StepPolicy {

    /**
     * 是否需要审批。
     */
    private Boolean requiresApproval;

    /**
     * 审批来源。
     */
    private String approvalSource;

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getApprovalSource() {
        return approvalSource;
    }

    public void setApprovalSource(String approvalSource) {
        this.approvalSource = approvalSource;
    }
}
