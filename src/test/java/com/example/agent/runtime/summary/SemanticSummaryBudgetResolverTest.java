package com.example.agent.runtime.summary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 语义摘要预算解析器测试。
 */
class SemanticSummaryBudgetResolverTest {

    @Test
    void shouldUseScenarioPolicyOverrides() {
        // 构建基础摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置摘要最大字符数。
        properties.setMaxChars(200);
        // 设置列表最大条目数。
        properties.setMaxListItems(5);

        // 构建场景化配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建默认场景策略。
        SemanticSummaryScenarioProperties.ScenarioPolicy defaultPolicy =
                new SemanticSummaryScenarioProperties.ScenarioPolicy();
        // 设置默认场景最大字符数。
        defaultPolicy.setMaxChars(150);
        // 写入默认场景策略。
        scenarioProperties.setDefaultPolicy(defaultPolicy);

        // 构建结果场景策略。
        SemanticSummaryScenarioProperties.ScenarioPolicy resultPolicy =
                new SemanticSummaryScenarioProperties.ScenarioPolicy();
        // 设置结果场景最大字符数。
        resultPolicy.setMaxChars(20);
        // 设置结果场景高亮最大条目数。
        resultPolicy.setMaxHighlights(1);
        // 写入结果场景策略。
        scenarioProperties.getPolicies().put("result:search", resultPolicy);

        // 构建预算解析器。
        SemanticSummaryBudgetResolver resolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建场景对象。
        SemanticSummaryScenario scenario = SemanticSummaryScenario.of("result:search");
        // 解析预算结果。
        SemanticSummaryBudget budget = resolver.resolveBudget(StepSummaryBuildInput.builder().build(), scenario);

        // 校验摘要最大字符数。
        assertEquals(20, budget.getMaxChars());
        // 校验高亮最大条目数。
        assertEquals(1, budget.getMaxHighlights());
        // 校验未解决问题条目数回退为默认值。
        assertEquals(5, budget.getMaxOpenQuestions());
    }
}
