package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import org.springframework.stereotype.Component;

/**
 * 复杂度阈值风险规则。
 */
@Component
public class ComplexityThresholdRiskRule implements CapabilityRiskRule {

    @Override
    public String ruleId() {
        return "complexity_threshold";
    }

    @Override
    public int priority() {
        return 10;
    }

    /**
     * 复杂度超过阈值时增加风险分。
     */
    @Override
    public double score(CapabilityEvaluationInput input,
                        double complexityScore,
                        CapabilityEvaluationProperties properties) {
        if (properties == null) {
            return 0;
        }
        if (complexityScore >= properties.getComplexityThreshold()) {
            return properties.getComplexityRiskWeight();
        }
        return 0;
    }
}
