package com.example.agent.governance.evaluation;

/**
 * 能力边界评估结果，包含风险等级与策略建议。
 */
public class CapabilityEvaluationResult {

    /**
     * 复杂度评分。
     */
    private double complexityScore;

    /**
     * 风险评分。
     */
    private double riskScore;

    /**
     * 风险等级。
     */
    private CapabilityRiskLevel riskLevel;

    /**
     * 推荐策略。
     */
    private String recommendedStrategy;

    /**
     * 是否需要审批。
     */
    private boolean shouldAskApproval;

    /**
     * 是否建议拆解任务。
     */
    private boolean shouldDecompose;

    /**
     * 需要提前停止的原因。
     */
    private String stopEarlyReason;

    /**
     * 是否跳过评估。
     */
    private boolean skipped;

    public double getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(double complexityScore) {
        this.complexityScore = complexityScore;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public CapabilityRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(CapabilityRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRecommendedStrategy() {
        return recommendedStrategy;
    }

    public void setRecommendedStrategy(String recommendedStrategy) {
        this.recommendedStrategy = recommendedStrategy;
    }

    public boolean isShouldAskApproval() {
        return shouldAskApproval;
    }

    public void setShouldAskApproval(boolean shouldAskApproval) {
        this.shouldAskApproval = shouldAskApproval;
    }

    public boolean isShouldDecompose() {
        return shouldDecompose;
    }

    public void setShouldDecompose(boolean shouldDecompose) {
        this.shouldDecompose = shouldDecompose;
    }

    public String getStopEarlyReason() {
        return stopEarlyReason;
    }

    public void setStopEarlyReason(String stopEarlyReason) {
        this.stopEarlyReason = stopEarlyReason;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }
}
