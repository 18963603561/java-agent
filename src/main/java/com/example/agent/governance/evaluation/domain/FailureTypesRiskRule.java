package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import org.springframework.stereotype.Component;

/**
 * 历史失败类型风险规则。
 */
@Component
public class FailureTypesRiskRule implements CapabilityRiskRule {

    @Override
    public String ruleId() {
        return "failure_types";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public double score(CapabilityEvaluationInput input,
                        double complexityScore,
                        CapabilityEvaluationProperties properties) {
        if (properties == null || input == null || input.getFailureTypes() == null || input.getFailureTypes().isEmpty()) {
            return 0;
        }
        double perFailure = Math.max(0, properties.getFailureTypeRiskWeightPerItem());
        double maxFailure = Math.max(0, properties.getFailureTypeRiskWeightMax());
        return Math.min(maxFailure, input.getFailureTypes().size() * perFailure);
    }
}
