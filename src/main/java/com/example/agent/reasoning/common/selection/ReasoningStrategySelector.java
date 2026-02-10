package com.example.agent.reasoning.common.selection;

import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.ReasoningInput;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理策略选择器。
 *
 * <p>用途：根据输入复杂度、风险等级与预算约束选择推理策略。
 */
@Component
public class ReasoningStrategySelector {

    private final ReasoningConfigResolver reasoningConfigResolver;

    public ReasoningStrategySelector(ReasoningConfigResolver reasoningConfigResolver) {
        this.reasoningConfigResolver = reasoningConfigResolver;
    }

    /**
     * 选择主策略。
     *
     * @param preferredStrategy 首选策略
     * @param input 输入上下文
     * @return 策略类型
     */
    public String selectPrimary(String preferredStrategy, Map<String, Object> input) {
        return selectPrimary(preferredStrategy, new ReasoningInput(input));
    }

    /**
     * 选择主策略。
     *
     * @param preferredStrategy 首选策略
     * @param input 输入上下文
     * @return 策略类型
     */
    public String selectPrimary(String preferredStrategy, ReasoningInput input) {
        String normalizedPreferred = normalize(preferredStrategy);
        if (StringUtils.hasText(normalizedPreferred)) {
            return normalizedPreferred;
        }

        double complexityThreshold = reasoningConfigResolver.resolveThoughtTreeComplexityThreshold();

        if (input != null) {
            if (isTrue(input.get("highRisk")) || isTrue(input.get("requiresDeliberation"))) {
                return "thought_tree";
            }
            if (isTrue(input.get("needDebate")) || isTrue(input.get("multiView"))) {
                return "debate";
            }
            Double complexityScore = input.getDouble("complexityScore");
            if (complexityScore != null && complexityScore > complexityThreshold) {
                return "thought_tree";
            }
        }
        return "cot";
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String strategy = value.trim().toLowerCase();
        return switch (strategy) {
            case "chain_of_thought", "cot" -> "cot";
            case "debate" -> "debate";
            case "thought_tree" -> "thought_tree";
            default -> null;
        };
    }
}
