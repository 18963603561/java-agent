package com.example.agent.reflection.model;

import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
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
        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("answer", "ok")).withSummary(Map.of(
                "outputDigest", Map.of(
                        "keyCount", 2,
                        "keys", java.util.List.of("answer", "status"),
                        "charCount", 128,
                        "truncated", false
                )
        ));

        ReflectionContext context = mapper.map(step, output, 1);

        assertNotNull(context);
        assertEquals("TOOL", context.getStepType());
        assertEquals(1, context.getAttempt());
        assertNotNull(context.getOutputSummary());
        assertTrue(context.getOutputSummary().getSummary().startsWith("digest:"));
        assertNotNull(context.getOutputDigest());
        assertEquals(2, context.getOutputDigest().getKeyCount());
    }

    @Test
    void mapUsesDigestFallbackWhenSummaryBlank() {
        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("answer", "ok")).withSummary(Map.of(
                "outputSummary", Map.of("summary", "   "),
                "outputDigest", Map.of(
                        "keyCount", 1,
                        "keys", java.util.List.of("answer"),
                        "charCount", 12,
                        "truncated", true
                )
        ));

        ReflectionContext context = mapper.map(step, output, 2);

        assertNotNull(context);
        assertNotNull(context.getOutputSummary());
        assertTrue(context.getOutputSummary().getSummary().contains("digest:"));
        assertNotNull(context.getOutputDigest());
        assertFalse(context.getOutputDigest().getKeys().isEmpty());
    }
}

