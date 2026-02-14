package com.example.agent.runtime.summary;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 摘要策略解析器测试。
 */
class SummaryStrategyResolverTest {

    @Test
    void shouldUseOverrideStrategyFirst() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置全局策略。
        properties.setStrategy("semantic");
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建场景策略。
        SemanticSummaryScenarioProperties.ScenarioPolicy scenarioPolicy =
                new SemanticSummaryScenarioProperties.ScenarioPolicy();
        // 设置场景策略。
        scenarioPolicy.setStrategy("template");
        // 写入场景策略映射。
        scenarioProperties.getPolicies().put("step:tool", scenarioPolicy);
        // 构建策略解析器。
        SummaryStrategyResolver resolver = new SummaryStrategyResolver(properties, scenarioProperties);
        // 构建覆盖配置映射。
        Map<String, Object> summaryOverrides = Map.of("strategy", "model");
        // 构建上下文映射。
        Map<String, Object> context = Map.of("summary", summaryOverrides);
        // 构建步骤输入映射。
        Map<String, Object> stepInput = Map.of("context", context);
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .stepInput(stepInput)
                .build();

        // 调用解析器解析策略。
        SummaryStrategyDecision decision = resolver.resolve(input, SemanticSummaryScenario.of("step:tool"));
        // 校验命中请求级覆盖策略。
        assertEquals(SummaryBuildStrategy.MODEL, decision.getStrategy());
        // 校验命中来源为请求级覆盖。
        assertEquals(SummaryStrategySource.OVERRIDE, decision.getSource());
    }

    @Test
    void shouldUseScenarioStrategyWhenNoOverride() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置全局策略。
        properties.setStrategy("semantic");
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建场景策略。
        SemanticSummaryScenarioProperties.ScenarioPolicy scenarioPolicy =
                new SemanticSummaryScenarioProperties.ScenarioPolicy();
        // 设置场景策略。
        scenarioPolicy.setStrategy("template");
        // 写入场景策略映射。
        scenarioProperties.getPolicies().put("result:search", scenarioPolicy);
        // 构建策略解析器。
        SummaryStrategyResolver resolver = new SummaryStrategyResolver(properties, scenarioProperties);
        // 构建空摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder().build();

        // 调用解析器解析策略。
        SummaryStrategyDecision decision = resolver.resolve(input, SemanticSummaryScenario.of("result:search"));
        // 校验命中场景策略。
        assertEquals(SummaryBuildStrategy.TEMPLATE, decision.getStrategy());
        // 校验命中来源为场景配置。
        assertEquals(SummaryStrategySource.SCENARIO, decision.getSource());
    }

    @Test
    void shouldUseGlobalStrategyWhenNoOverrideAndNoScenario() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置全局策略。
        properties.setStrategy("model");
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建策略解析器。
        SummaryStrategyResolver resolver = new SummaryStrategyResolver(properties, scenarioProperties);
        // 构建空摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder().build();

        // 调用解析器解析策略。
        SummaryStrategyDecision decision = resolver.resolve(input, SemanticSummaryScenario.DEFAULT);
        // 校验命中全局策略。
        assertEquals(SummaryBuildStrategy.MODEL, decision.getStrategy());
        // 校验命中来源为全局配置。
        assertEquals(SummaryStrategySource.GLOBAL, decision.getSource());
    }
}

