package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.governance.evaluation.CapabilityRiskLevel;
import org.springframework.stereotype.Component;

/**
 * 高风险思维树策略规则。
 */
@Component
public class HighRiskThoughtTreeStrategyRule implements CapabilityStrategyRule {

    @Override
    public String ruleId() {
        return "high_risk_thought_tree";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public String resolve(CapabilityEvaluationInput input,
                          double complexityScore,
                          CapabilityRiskLevel riskLevel,
                          CapabilityEvaluationProperties properties) {
        if (properties == null) {
            return null;
        }
        if (riskLevel == CapabilityRiskLevel.HIGH || complexityScore >= properties.getComplexityThreshold()) {
            return properties.getHighRiskStrategy();
        }
        return null;
    }
}
