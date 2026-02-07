package com.example.agent.runtime;

import com.example.agent.runtime.llm.LlmDecisionService;
import com.example.agent.runtime.llm.LlmStepService;
import com.example.agent.runtime.llm.ToolCallOrchestrator;
import com.example.agent.runtime.llm.ToolOutputAssembler;
import com.example.agent.runtime.llm.ToolSummaryService;
import com.example.agent.runtime.output.OutputKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ToolOutputAssemblerTest {

    @Test
    void buildAnswerOutputContainsModeAndRawRef() {
        ToolOutputAssembler assembler = new ToolOutputAssembler();
        LlmDecisionService decisionService = new LlmDecisionService(new ObjectMapper());

        Map<String, Object> output = assembler.buildAnswerOutput(
                Map.of("answer", "ok", "confidence", 0.9),
                "raw",
                "llm_step",
                "ref-1",
                decisionService
        );

        assertEquals("answer", output.get("mode"));
        assertEquals("ok", output.get("answer"));
        assertEquals("ref-1", output.get(OutputKeys.RAW_REF));
        assertTrue(output.containsKey(OutputKeys.REFS));
    }

    @Test
    void buildToolOutputAndDefaultsFillRequiredFields() {
        ToolOutputAssembler assembler = new ToolOutputAssembler();
        ToolSummaryService summaryService = mock(ToolSummaryService.class);

        ToolCallOrchestrator.ToolCallOutcome outcome = ToolCallOrchestrator.ToolCallOutcome.success(
                Map.of("answer", "tool-ok")
        );

        Map<String, Object> output = assembler.buildToolOutput(
                LlmStepService.ToolSummaryMode.RAW,
                "toolA",
                Map.of("q", "x"),
                outcome,
                "llm_step",
                summaryService
        );
        assembler.applyToolOutputDefaults(
                output,
                "toolA",
                Map.of("q", "x"),
                outcome,
                "llm_step",
                "decision-ref",
                "summary-ref",
                summaryService
        );

        assertEquals("tool_call", output.get("mode"));
        assertEquals("SUCCESS", output.get("toolStatus"));
        assertEquals("toolA", output.get(OutputKeys.TOOL_NAME));
        assertNotNull(output.get(OutputKeys.RAW_RESULT));
        assertTrue(output.containsKey(OutputKeys.REFS));
    }

    @Test
    void mergeRefAddsRefToRefsMap() {
        ToolOutputAssembler assembler = new ToolOutputAssembler();
        Map<String, Object> output = new java.util.HashMap<>();

        assembler.mergeRef(output, "k1", "v1");

        assertTrue(output.containsKey(OutputKeys.REFS));
        Object refsObj = output.get(OutputKeys.REFS);
        assertTrue(refsObj instanceof Map<?, ?>);
        Map<?, ?> refs = (Map<?, ?>) refsObj;
        assertEquals("v1", refs.get("k1"));
    }
}
