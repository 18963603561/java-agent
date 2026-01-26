package com.example.agent.policy;

import java.util.List;

/**
 * 策略评估结果。
 */
public class PolicyDecision {

    private String policyId;
    private String decision;
    private String reason;
    private String evaluationId;
    private List<String> matchedRules;

    public PolicyDecision() {
    }

    public PolicyDecision(String policyId, String decision, String reason,
                          String evaluationId, List<String> matchedRules) {
        this.policyId = policyId;
        this.decision = decision;
        this.reason = reason;
        this.evaluationId = evaluationId;
        this.matchedRules = matchedRules;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
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

    public String getEvaluationId() {
        return evaluationId;
    }

    public void setEvaluationId(String evaluationId) {
        this.evaluationId = evaluationId;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }

    public void setMatchedRules(List<String> matchedRules) {
        this.matchedRules = matchedRules;
    }
}
