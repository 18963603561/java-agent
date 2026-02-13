package com.example.agent.reflection.model;

import com.example.agent.runtime.contract.RuntimeOutputKeys;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionContextMapperTest {

    private final ReflectionContextMapper mapper = new ReflectionContextMapper();

    @Test
    void mapSupportsNullStepAndNullOutput() {
        ReflectionContext context = mapper.map(null, null, 0);

        assertNotNull(context);
        assertNull(context.getStepType());
        assertEquals(0, context.getAttempt());
        assertNotNull(context.getOutputSummary());
        assertEquals("(summary disabled)", context.getOutputSummary().getSummary());
        assertNotNull(context.getOutputDigest());
        assertTrue(context.getOutputDigest().getKeys().isEmpty());
    }

    @Test
    void mapBuildsDigestSummaryWhenSummaryMissing() {
        // 构建步骤定义，用于反思上下文映射。
        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        // 构建语义摘要映射，用于提供摘要文本。
        Map<String, Object> summary = Map.of(
                RuntimeOutputKeys.SUMMARY_TEXT, "语义摘要",
                RuntimeOutputKeys.TRUNCATED, false
        );
        // 构建步骤输出载荷映射。
        Map<String, Object> payload = Map.of("answer", "ok");
        // 生成步骤输出对象。
        StepExecutionOutput output = StepExecutionOutput.fromPayload(payload);
        // 注入语义摘要映射到步骤输出。
        StepExecutionOutput outputWithSummary = output.withSummary(summary);
        // 调用映射器生成反思上下文。
        ReflectionContext context = mapper.map(step, outputWithSummary, 1);

        // 校验上下文对象不为空。
        assertNotNull(context);
        // 读取步骤类型。
        String stepType = context.getStepType();
        // 校验步骤类型正确。
        assertEquals("TOOL", stepType);
        // 读取尝试次数。
        Integer attempt = context.getAttempt();
        // 校验尝试次数正确。
        assertEquals(1, attempt);
        // 读取输出摘要对象。
        ReflectionOutputSummary outputSummary = context.getOutputSummary();
        // 校验输出摘要对象不为空。
        assertNotNull(outputSummary);
        // 读取摘要文本。
        String summaryText = outputSummary.getSummary();
        // 校验摘要文本等于语义摘要。
        assertEquals("语义摘要", summaryText);
        // 读取输出指纹对象。
        ReflectionOutputDigest outputDigest = context.getOutputDigest();
        // 校验输出指纹对象不为空。
        assertNotNull(outputDigest);
        // 读取指纹键列表。
        List<String> keys = outputDigest.getKeys();
        // 校验指纹键列表为空。
        assertTrue(keys.isEmpty());
    }

    @Test
    void mapUsesDigestFallbackWhenSummaryBlank() {
        // 构建步骤定义，用于反思上下文映射。
        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        // 构建语义摘要映射，模拟空摘要文本。
        Map<String, Object> summary = Map.of(
                RuntimeOutputKeys.SUMMARY_TEXT, "   ",
                RuntimeOutputKeys.TRUNCATED, true
        );
        // 构建步骤输出载荷映射。
        Map<String, Object> payload = Map.of("answer", "ok");
        // 生成步骤输出对象。
        StepExecutionOutput output = StepExecutionOutput.fromPayload(payload);
        // 注入空摘要映射到步骤输出。
        StepExecutionOutput outputWithSummary = output.withSummary(summary);
        // 调用映射器生成反思上下文。
        ReflectionContext context = mapper.map(step, outputWithSummary, 2);

        // 校验上下文对象不为空。
        assertNotNull(context);
        // 读取输出摘要对象。
        ReflectionOutputSummary outputSummary = context.getOutputSummary();
        // 校验输出摘要对象不为空。
        assertNotNull(outputSummary);
        // 读取摘要文本。
        String summaryText = outputSummary.getSummary();
        // 校验摘要文本为默认占位。
        assertEquals("(summary disabled)", summaryText);
        // 读取输出指纹对象。
        ReflectionOutputDigest outputDigest = context.getOutputDigest();
        // 校验输出指纹对象不为空。
        assertNotNull(outputDigest);
        // 读取指纹键列表。
        List<String> keys = outputDigest.getKeys();
        // 校验指纹键列表为空。
        assertTrue(keys.isEmpty());
    }
}
