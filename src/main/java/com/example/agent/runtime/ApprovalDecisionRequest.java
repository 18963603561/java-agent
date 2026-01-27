package com.example.agent.runtime;

/**
 * 审批决策请求。
 */
public class ApprovalDecisionRequest {

    /**
     * 决策结果，建议使用 approve 或 reject。
     */
    private String decision;

    /**
     * 决策原因，可选。
     */
    private String reason;

    public ApprovalDecisionRequest() {
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
