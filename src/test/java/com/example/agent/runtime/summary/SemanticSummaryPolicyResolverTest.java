package com.example.agent.runtime.summary;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 语义摘要场景解析器测试。
 */
class SemanticSummaryPolicyResolverTest {

    @Test
    void shouldResolveScenarioByResultKind() {
        // 构建场景解析器。
        SemanticSummaryPolicyResolver resolver = new SemanticSummaryPolicyResolver();
        // 构建结果映射。
        Map<String, Object> result = Map.of("kind", "SEARCH");
        // 构建输出映射。
        Map<String, Object> output = Map.of(RuntimeOutputKeys.RESULT, result);
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .output(output)
                .build();

        // 调用解析器解析场景。
        SemanticSummaryScenario scenario = resolver.resolveScenario(input);
        // 校验场景编码。
        assertEquals("result:search", scenario.getCode());
    }

    @Test
    void shouldResolveScenarioByDecision() {
        // 构建场景解析器。
        SemanticSummaryPolicyResolver resolver = new SemanticSummaryPolicyResolver();
        // 构建输出映射，包含决策字段。
        Map<String, Object> output = Map.of(RuntimeOutputKeys.DECISION, Map.of("next_action", "CALL_TOOL"));
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .output(output)
                .build();

        // 调用解析器解析场景。
        SemanticSummaryScenario scenario = resolver.resolveScenario(input);
        // 校验场景编码。
        assertEquals("decision", scenario.getCode());
    }

    @Test
    void shouldResolveScenarioByStepType() {
        // 构建场景解析器。
        SemanticSummaryPolicyResolver resolver = new SemanticSummaryPolicyResolver();
        // 构建摘要输入。
        StepSummaryBuildInput input = StepSummaryBuildInput.builder()
                .stepType("TOOL")
                .build();

        // 调用解析器解析场景。
        SemanticSummaryScenario scenario = resolver.resolveScenario(input);
        // 校验场景编码。
        assertEquals("step:tool", scenario.getCode());
    }
}
