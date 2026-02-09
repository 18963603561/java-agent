package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.governance.evaluation.CapabilityRiskLevel;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 调研关键词策略规则。
 */
@Component
public class ResearchKeywordStrategyRule implements CapabilityStrategyRule {

    @Override
    public String ruleId() {
        return "research_keyword";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public String resolve(CapabilityEvaluationInput input,
                          double complexityScore,
                          CapabilityRiskLevel riskLevel,
                          CapabilityEvaluationProperties properties) {
        if (input == null || properties == null || !StringUtils.hasText(input.getTaskDescription())) {
            return null;
        }
        String lower = input.getTaskDescription().toLowerCase(Locale.ROOT);
        List<String> keywords = properties.getResearchKeywords();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return "research";
            }
        }
        return null;
    }
}
