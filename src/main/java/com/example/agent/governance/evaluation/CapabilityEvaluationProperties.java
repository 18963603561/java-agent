package com.example.agent.governance.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 能力边界评估配置，用于控制评估启用与风险阈值。
 */
@Component
@ConfigurationProperties(prefix = "agent.capability-evaluation")
public class CapabilityEvaluationProperties {

    /**
     * 是否启用能力边界评估。
     */
    private boolean enabled = true;

    /**
     * 风险阈值，超过该值视为高风险。
     */
    private double riskThreshold = 0.7;

    /**
     * 复杂度阈值，超过该值触发高复杂度策略。
     */
    private double complexityThreshold = 0.7;

    /**
     * 高风险时是否强制触发审批。
     */
    private boolean forceApprovalAboveRisk = true;

    /**
     * 预算阈值，用于风险评估参考。
     */
    private int budgetThresholdTokens = 10000;

    /**
     * 默认推荐策略。
     */
    private String defaultStrategy = "tool";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getRiskThreshold() {
        return riskThreshold;
    }

    public void setRiskThreshold(double riskThreshold) {
        this.riskThreshold = riskThreshold;
    }

    public double getComplexityThreshold() {
        return complexityThreshold;
    }

    public void setComplexityThreshold(double complexityThreshold) {
        this.complexityThreshold = complexityThreshold;
    }

    public boolean isForceApprovalAboveRisk() {
        return forceApprovalAboveRisk;
    }

    public void setForceApprovalAboveRisk(boolean forceApprovalAboveRisk) {
        this.forceApprovalAboveRisk = forceApprovalAboveRisk;
    }

    public int getBudgetThresholdTokens() {
        return budgetThresholdTokens;
    }

    public void setBudgetThresholdTokens(int budgetThresholdTokens) {
        this.budgetThresholdTokens = budgetThresholdTokens;
    }

    public String getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(String defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }
}
