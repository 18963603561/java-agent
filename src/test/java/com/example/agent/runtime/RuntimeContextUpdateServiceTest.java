package com.example.agent.runtime;

import com.example.agent.runtime.engine.RuntimeContextUpdateService;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultSummary;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.summary.StepSummaryProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextUpdateServiceTest {

    @Test
    void mergeStepInputResolvesApprovalFieldsFromTypedView() {
        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder envelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService service = new RuntimeContextUpdateService(envelopeBuilder);

        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.asMap().put("k", "v");

        StepSpec step = new StepSpec();
        step.setArguments(Map.of("a", 1));
        step.setContext(Map.of("requiresApproval", true, "approvalSource", "evaluation"));

        Map<String, Object> merged = service.mergeStepInput(step, runtimeContext);

        assertEquals("v", merged.get("k"));
        assertEquals(1, merged.get("a"));
        assertEquals(true, merged.get("requiresApproval"));
        assertEquals("evaluation", merged.get("approvalSource"));
    }

    @Test
    void updateRuntimeContextWritesRawAndExecutedSteps() {
        // 构建摘要配置对象。
        StepSummaryProperties properties = new StepSummaryProperties();
        // 设置原始输出最大字符数。
        properties.setRawMaxChars(2000);
        // 启用原始输出快照。
        properties.setRawEnable(true);
        // 构建原始输出封装器。
        RawOutputEnvelopeBuilder envelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        // 构建运行时上下文更新服务。
        RuntimeContextUpdateService service = new RuntimeContextUpdateService(envelopeBuilder);

        // 构建运行时上下文对象。
        RuntimeContext runtimeContext = new RuntimeContext();
        // 构建步骤记录对象。
        StepRecord record = new StepRecord();
        // 设置步骤标识。
        record.setStepId("step-1");
        // 设置步骤类型。
        record.setType("LLM");
        // 设置步骤尝试次数。
        record.setAttempt(1);
        // 设置步骤状态。
        record.setStatus(StepState.COMPLETED);

        // 构建步骤结果对象。
        StepResult stepResult = new StepResult();
        // 构建步骤摘要对象。
        StepResultSummary summary = new StepResultSummary();
        // 设置摘要映射。
        summary.setStepSummary(Map.of("summary", "ok"));
        // 绑定摘要到步骤结果。
        stepResult.setSummary(summary);
        // 绑定步骤结果到记录。
        record.setOutput(stepResult);

        // 构建步骤输出对象。
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "answer", "done",
                "highlights", "h",
                "mode", "answer",
                OutputKeys.TOOL_NAME, "demo_tool",
                OutputKeys.RAW_REF, "mem://raw/1"
        ));

        // 调用运行时上下文更新。
        service.updateRuntimeContext(runtimeContext, record, output);

        // 校验最近步骤标识写入。
        assertEquals("step-1", runtimeContext.getLastStepId());
        // 校验最近步骤类型写入。
        assertEquals("LLM", runtimeContext.getLastStepType());
        // 校验最近原始输出快照存在。
        assertNotNull(runtimeContext.getLastStepRawOutput());
        // 校验原始引用写入正确。
        assertEquals("mem://raw/1", runtimeContext.getLastStepRawRef());
        // 校验原始输出未被截断。
        assertFalse(runtimeContext.isLastStepRawTruncated());

        // 读取步骤列表对象。
        Object stepsObj = runtimeContext.asMap().get("steps");
        // 校验步骤列表类型正确。
        assertTrue(stepsObj instanceof List<?>);
        // 转换步骤列表对象。
        List<?> steps = (List<?>) stepsObj;
        // 校验步骤数量。
        assertEquals(1, steps.size());
        // 校验步骤条目类型正确。
        assertTrue(steps.get(0) instanceof Map<?, ?>);
        // 转换步骤条目对象。
        Map<?, ?> item = (Map<?, ?>) steps.get(0);
        // 校验步骤标识字段。
        assertEquals("step-1", item.get("stepId"));
        // 校验步骤类型字段。
        assertEquals("LLM", item.get("type"));
        // 校验工具名称字段。
        assertEquals("demo_tool", item.get(OutputKeys.TOOL_NAME));
    }

    @Test
    void recordStepOutputIgnoresNulls() {
        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder envelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService service = new RuntimeContextUpdateService(envelopeBuilder);

        List<StepResult> outputs = new java.util.ArrayList<>();
        service.recordStepOutput(outputs, null);
        assertEquals(0, outputs.size());

        StepRecord record = new StepRecord();
        service.recordStepOutput(outputs, record);
        assertEquals(0, outputs.size());

        StepResult result = new StepResult();
        record.setOutput(result);
        service.recordStepOutput(outputs, record);
        assertEquals(1, outputs.size());
    }

    @Test
    void resolveStepInputReturnsNullWhenEmpty() {
        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder envelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService service = new RuntimeContextUpdateService(envelopeBuilder);

        StepSpec step = new StepSpec();
        assertNull(service.resolveStepInput(step));
    }
}
