package com.example.agent.runtime.summary;

import com.example.agent.runtime.model.SemanticSummary;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义摘要服务测试。
 */
class SemanticSummaryServiceTest {

    @Test
    void shouldKeepTextWhenMaxCharsIsZero() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置最大字符数为 0，表示不限制。
        properties.setMaxChars(0);
        // 构建场景解析器。
        SemanticSummaryPolicyResolver policyResolver = new SemanticSummaryPolicyResolver();
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver budgetResolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建语义摘要服务。
        SemanticSummaryService service = new SemanticSummaryService(properties, policyResolver, budgetResolver);
        // 构建输出映射。
        Map<String, Object> output = Map.of("answer", "abcdefghijklmnopqrstuvwxyz");
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .output(output)
                .build();

        // 调用语义摘要服务生成摘要。
        SemanticSummary summary = service.buildSummary(input);
        // 校验文本未被抑制。
        assertEquals("abcdefghijklmnopqrstuvwxyz", summary.getText());
        // 校验未发生截断。
        assertFalse(summary.isTruncated());
    }

    @Test
    void shouldTruncateTextWhenMaxCharsIsPositive() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置最大字符数。
        properties.setMaxChars(5);
        // 构建场景解析器。
        SemanticSummaryPolicyResolver policyResolver = new SemanticSummaryPolicyResolver();
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver budgetResolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建语义摘要服务。
        SemanticSummaryService service = new SemanticSummaryService(properties, policyResolver, budgetResolver);
        // 构建输出映射。
        Map<String, Object> output = Map.of("answer", "abcdefghijklmnopqrstuvwxyz");
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .output(output)
                .build();

        // 调用语义摘要服务生成摘要。
        SemanticSummary summary = service.buildSummary(input);
        // 校验文本按预算截断。
        assertEquals("abcde", summary.getText());
        // 校验发生截断。
        assertTrue(summary.isTruncated());
    }
}
