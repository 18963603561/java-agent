package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 步骤摘要构建器策略测试。
 */
class StepOutputSummaryBuilderStrategyTest {

    @Test
    void shouldReturnEmptySummaryWhenStrategyOff() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置默认策略为语义摘要。
        properties.setStrategy("semantic");
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建场景解析器。
        SemanticSummaryPolicyResolver policyResolver = new SemanticSummaryPolicyResolver();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver budgetResolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建语义摘要服务。
        SemanticSummaryService summaryService = new SemanticSummaryService(properties, policyResolver, budgetResolver);
        // 构建策略解析器。
        SummaryStrategyResolver strategyResolver = new SummaryStrategyResolver(properties, scenarioProperties);
        // 构建步骤摘要构建器。
        StepOutputSummaryBuilder builder = new StepOutputSummaryBuilder(properties, summaryService, null, null, null,
                strategyResolver);
        // 构建覆盖配置。
        Map<String, Object> summaryOverrides = Map.of("strategy", "off");
        // 构建步骤输入。
        Map<String, Object> stepInput = Map.of("context", Map.of("summary", summaryOverrides));
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .stepInput(stepInput)
                .output(Map.of("answer", "hello"))
                .build();

        // 调用构建器生成摘要。
        Map<String, Object> summaryMap = builder.build(input);
        // 校验摘要为空映射。
        assertTrue(summaryMap.isEmpty());
    }

    @Test
    void shouldBuildTemplateSummaryWhenStrategyTemplate() {
        // 构建摘要配置。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置默认策略为语义摘要。
        properties.setStrategy("semantic");
        // 构建场景配置。
        SemanticSummaryScenarioProperties scenarioProperties = new SemanticSummaryScenarioProperties();
        // 构建场景解析器。
        SemanticSummaryPolicyResolver policyResolver = new SemanticSummaryPolicyResolver();
        // 构建预算解析器。
        SemanticSummaryBudgetResolver budgetResolver = new SemanticSummaryBudgetResolver(properties, scenarioProperties);
        // 构建语义摘要服务。
        SemanticSummaryService summaryService = new SemanticSummaryService(properties, policyResolver, budgetResolver);
        // 构建策略解析器。
        SummaryStrategyResolver strategyResolver = new SummaryStrategyResolver(properties, scenarioProperties);
        // 构建步骤摘要构建器。
        StepOutputSummaryBuilder builder = new StepOutputSummaryBuilder(properties, summaryService, null, null, null,
                strategyResolver);
        // 构建输出映射。
        Map<String, Object> output = new LinkedHashMap<>();
        // 写入回答文本。
        output.put("answer", "查询成功");
        // 构建覆盖配置。
        Map<String, Object> summaryOverrides = Map.of("strategy", "template");
        // 构建步骤输入。
        Map<String, Object> stepInput = Map.of("context", Map.of("summary", summaryOverrides));
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .status("COMPLETED")
                .stepType("TOOL")
                .toolName("search-user-by-name")
                .stepInput(stepInput)
                .output(output)
                .build();

        // 调用构建器生成摘要。
        Map<String, Object> summaryMap = builder.build(input);
        // 读取摘要文本。
        String text = String.valueOf(summaryMap.get(RuntimeOutputKeys.SUMMARY_TEXT));
        // 校验模板前缀已生效。
        assertTrue(text.contains("步骤类型=TOOL"));
        // 校验模板文本保留原始语义摘要文本。
        assertTrue(text.contains("查询成功"));
        // 校验截断标记存在。
        assertEquals(Boolean.FALSE, summaryMap.get(RuntimeOutputKeys.TRUNCATED));
    }
}

