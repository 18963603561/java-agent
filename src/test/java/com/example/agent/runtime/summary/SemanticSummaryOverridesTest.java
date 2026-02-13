package com.example.agent.runtime.summary;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 摘要覆盖配置相关测试。
 */
class SemanticSummaryOverridesTest {

    @Test
    void shouldResolveScenarioFromOverride() {
        // 构建场景解析器。
        SemanticSummaryPolicyResolver resolver = new SemanticSummaryPolicyResolver();
        // 构建覆盖配置映射。
        Map<String, Object> overrides = Map.of("scenario", "step:tool");
        // 构建上下文映射。
        Map<String, Object> context = Map.of("summary", overrides);
        // 构建步骤输入映射。
        Map<String, Object> stepInput = Map.of("context", context);
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .stepInput(stepInput)
                .build();

        // 调用解析器解析场景决策。
        SemanticSummaryScenarioDecision decision = resolver.resolveScenarioDecision(input);
        // 校验场景编码。
        assertEquals("step:tool", decision.getScenario().getCode());
        // 校验场景来源。
        assertEquals(SemanticSummaryScenarioSource.OVERRIDE, decision.getSource());
    }

    @Test
    void shouldApplyBudgetOverrides() {
        // 构建基础摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置默认最大字符数。
        properties.setMaxChars(200);
        // 设置默认列表最大条目数。
        properties.setMaxListItems(5);
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver resolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建覆盖配置映射。
        Map<String, Object> overrides = Map.of("maxChars", 10, "maxListItems", 2);
        // 构建上下文映射。
        Map<String, Object> context = Map.of("summary", overrides);
        // 构建步骤输入映射。
        Map<String, Object> stepInput = Map.of("context", context);
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .stepInput(stepInput)
                .build();

        // 调用预算解析器解析预算。
        SemanticSummaryBudget budget = resolver.resolveBudget(input, SemanticSummaryScenario.DEFAULT);
        // 校验最大字符数覆盖。
        assertEquals(10, budget.getMaxChars());
        // 校验高亮列表覆盖。
        assertEquals(2, budget.getMaxHighlights());
        // 校验未解决问题列表覆盖。
        assertEquals(2, budget.getMaxOpenQuestions());
        // 校验风险列表覆盖。
        assertEquals(2, budget.getMaxRisks());
        // 校验来源引用列表覆盖。
        assertEquals(2, budget.getMaxSourceRefs());
    }
}
