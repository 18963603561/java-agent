package com.example.agent.runtime;

import com.example.agent.runtime.step.contract.StepExecutionOutput;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StepExecutionOutputTest {

    @Test
    void fromPayloadPrefersToolNameOverTool() {
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "tool", "legacy-tool",
                "toolName", "tool-name"
        ));

        assertEquals("tool-name", output.getToolName());
    }

    @Test
    void fromPayloadFallsBackToToolWhenToolNameMissing() {
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "tool", "legacy-tool"
        ));

        assertEquals("legacy-tool", output.getToolName());
    }

    @Test
    void fromPayloadResolvesToolNameFromToolPayloadWhenToolIsObject() {
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "tool", Map.of(
                        "name", "payload-tool",
                        "arguments", Map.of("k", "v")
                )
        ));

        assertEquals("payload-tool", output.getToolName());
    }

    @Test
    void fromPayloadResolvesRawRefWithExpectedPrecedence() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("rawRef", "top");
        payload.put("rawResult", Map.of("rawRef", "rawResult"));
        payload.put("result", Map.of("rawRef", "result"));
        payload.put("raw", Map.of("rawRef", "raw"));

        StepExecutionOutput output = StepExecutionOutput.fromPayload(payload);
        assertEquals("top", output.getRawRef());

        payload.remove("rawRef");
        output = StepExecutionOutput.fromPayload(payload);
        assertEquals("rawResult", output.getRawRef());

        payload.remove("rawResult");
        output = StepExecutionOutput.fromPayload(payload);
        assertEquals("result", output.getRawRef());

        payload.remove("result");
        output = StepExecutionOutput.fromPayload(payload);
        assertEquals("raw", output.getRawRef());

        payload.clear();
        output = StepExecutionOutput.fromPayload(payload);
        assertNull(output.getRawRef());
    }

    @Test
    void typedGettersReturnExpectedValues() {
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "answer", "done",
                "mode", "tool_call",
                "toolStatus", "ok",
                "refs", Map.of(
                        "decisionRawRef", "rawref:v1:mem:decision",
                        "summaryRawRef", "rawref:v1:mem:summary"
                )
        ));

        assertEquals("done", output.getAnswerText());
        assertEquals("tool_call", output.getMode());
        assertEquals("ok", output.getToolStatus());
        assertEquals("rawref:v1:mem:decision", output.getDecisionRawRef());
        assertEquals("rawref:v1:mem:summary", output.getSummaryRawRef());
    }
}
