package com.example.agent.reflection.parser;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReflectionResponseParserTest {

    @Test
    void parseReturnsFailureWhenScoreMissing() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParseOutcome outcome = parser.parse("{\"retry\":true,\"notes\":\"x\"}");

        assertNotNull(outcome);
        assertFalse(outcome.isSuccess());
        assertEquals("missing_field", outcome.getErrorType());
    }

    @Test
    void parseReturnsFailureWhenScoreTypeInvalid() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParseOutcome outcome = parser.parse("{\"score\":\"0.2\",\"retry\":true,\"notes\":\"x\"}");

        assertNotNull(outcome);
        assertFalse(outcome.isSuccess());
        assertEquals("missing_field", outcome.getErrorType());
    }

    @Test
    void parseReturnsFailureWhenScoreOutOfRange() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParseOutcome negative = parser.parse("{\"score\":-0.1,\"retry\":false,\"notes\":\"n\"}");
        ReflectionParseOutcome overflow = parser.parse("{\"score\":1.1,\"retry\":false,\"notes\":\"n\"}");

        assertNotNull(negative);
        assertNotNull(overflow);
        assertFalse(negative.isSuccess());
        assertFalse(overflow.isSuccess());
        assertEquals("score_out_of_range", negative.getErrorType());
        assertEquals("score_out_of_range", overflow.getErrorType());
    }

    @Test
    void parseTreatsInvalidRetryAndNotesAsDefault() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParseOutcome outcome = parser.parse("{\"score\":0.6,\"retry\":\"true\",\"notes\":{\"x\":1}}");

        assertNotNull(outcome);
        assertTrue(outcome.isSuccess());
        ReflectionParsingResult result = outcome.getParsingResult();
        assertNotNull(result);
        assertEquals(0.6, result.getScore(), 0.0001);
        assertFalse(result.isRetry());
        assertEquals("llm_reflection", result.getNotes());
    }

    @Test
    void parseReturnsJsonParseErrorWhenContentNotJson() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParseOutcome outcome = parser.parse("not json");
        assertNotNull(outcome);
        assertFalse(outcome.isSuccess());
        assertEquals("json_parse_error", outcome.getErrorType());
    }

    @Test
    void resolveParseErrorTypeReturnsEmptyOutputForBlank() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        assertEquals("empty_output", parser.resolveParseErrorType("   "));
    }

    @Test
    void resolveParseErrorTypeReturnsMissingFieldForNonBlank() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        assertEquals("json_parse_error", parser.resolveParseErrorType("text"));
    }

    @Test
    void resolveParseErrorTypeReturnsScoreOutOfRangeWhenScoreOverflow() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        assertEquals("score_out_of_range", parser.resolveParseErrorType("{\"score\":1.5,\"retry\":false}"));
    }

    @Test
    void tryRepairReturnsNullWhenRawContentBlank() {
        ReflectionResponseParser parser = createParserWithNoRepair();

        ReflectionParsingResult result = parser.tryRepair("  ", null);

        assertNull(result);
    }

    @Test
    void tryRepairReturnsParsedWhenRepairServiceReturnsValidJson() {
        JsonOutputRepairService repairService = createRepairServiceReturning("{\"score\":0.7,\"retry\":true,\"notes\":\"fixed\"}");
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);
        ReflectionExecutionContext context = ReflectionExecutionContext.builder()
                .step(new StepSpec("TOOL", Map.of("tool", "search")))
                .output(StepExecutionOutput.fromPayload(Map.of("result", "ok")))
                .attempt(1)
                .build();

        ReflectionParsingResult result = parser.tryRepair("raw broken", context);

        assertNotNull(result);
        assertEquals(0.7, result.getScore(), 0.0001);
        assertEquals("fixed", result.getNotes());
    }

    @Test
    void tryRepairWithNullContextDoesNotThrowAndReturnsResult() {
        JsonOutputRepairService repairService = createRepairServiceReturning("{\"score\":0.55,\"retry\":false,\"notes\":\"ok\"}");
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);

        ReflectionParsingResult result = parser.tryRepair("raw broken", null);

        assertNotNull(result);
        assertEquals(0.55, result.getScore(), 0.0001);
    }

    private ReflectionResponseParser createParserWithNoRepair() {
        return new ReflectionResponseParser(new ObjectMapper(), null);
    }

    private JsonOutputRepairService createRepairServiceReturning(String repairedJson) {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("m1", repairedJson, 1, 1));

        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        when(promptAssembler.build(any(), any(), any())).thenReturn(null);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        return new JsonOutputRepairService(modelInvocationService, promptAssembler, metricsPublisher);
    }
}
