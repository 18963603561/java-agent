package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工具摘要缺失风险规则。
 */
@Component
public class MissingToolSummaryRiskRule implements CapabilityRiskRule {

    @Override
    public String ruleId() {
        return "missing_tool_summary";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public double score(CapabilityEvaluationInput input,
                        double complexityScore,
                        CapabilityEvaluationProperties properties) {
        if (properties == null) {
            return 0;
        }
        if (input == null || !StringUtils.hasText(input.getToolSummary())) {
            return properties.getMissingToolSummaryRiskWeight();
        }
        return 0;
    }
}
