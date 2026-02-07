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
        StepSummaryProperties properties = new StepSummaryProperties();
        properties.setRawMaxChars(2000);
        RawOutputEnvelopeBuilder envelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService service = new RuntimeContextUpdateService(envelopeBuilder);

        RuntimeContext runtimeContext = new RuntimeContext();
        StepRecord record = new StepRecord();
        record.setStepId("step-1");
        record.setType("LLM");
        record.setAttempt(1);
        record.setStatus(StepState.COMPLETED);

        StepResult stepResult = new StepResult();
        StepResultSummary summary = new StepResultSummary();
        summary.setStepSummary(Map.of("summary", "ok"));
        stepResult.setSummary(summary);
        record.setOutput(stepResult);

        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "answer", "done",
                "highlights", "h",
                "mode", "answer",
                OutputKeys.TOOL_NAME, "demo_tool",
                OutputKeys.RAW_REF, "mem://raw/1"
        ));

        service.updateRuntimeContext(runtimeContext, record, output);

        assertEquals("step-1", runtimeContext.getLastStepId());
        assertEquals("LLM", runtimeContext.getLastStepType());
        assertNotNull(runtimeContext.getLastStepRawOutput());
        assertEquals("mem://raw/1", runtimeContext.getLastStepRawRef());
        assertFalse(runtimeContext.isLastStepRawTruncated());

        Object stepsObj = runtimeContext.asMap().get("steps");
        assertTrue(stepsObj instanceof List<?>);
        List<?> steps = (List<?>) stepsObj;
        assertEquals(1, steps.size());
        assertTrue(steps.get(0) instanceof Map<?, ?>);
        Map<?, ?> item = (Map<?, ?>) steps.get(0);
        assertEquals("step-1", item.get("stepId"));
        assertEquals("LLM", item.get("type"));
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
