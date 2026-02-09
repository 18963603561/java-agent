package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import org.springframework.stereotype.Component;

/**
 * 预算压力风险规则。
 */
@Component
public class BudgetPressureRiskRule implements CapabilityRiskRule {

    @Override
    public String ruleId() {
        return "budget_pressure";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public double score(CapabilityEvaluationInput input,
                        double complexityScore,
                        CapabilityEvaluationProperties properties) {
        if (properties == null) {
            return 0;
        }
        int budgetThreshold = input != null && input.getBudgetThresholdTokens() > 0
                ? input.getBudgetThresholdTokens()
                : properties.getBudgetThresholdTokens();
        if (budgetThreshold > 0
                && complexityScore >= properties.getBudgetRiskComplexityThreshold()
                && budgetThreshold < properties.getBudgetRiskTokenThreshold()) {
            return properties.getBudgetRiskWeight();
        }
        return 0;
    }
}
