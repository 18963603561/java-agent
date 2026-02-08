package com.example.agent.planning.parser;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlanParserTest {

    @ParameterizedTest
    @MethodSource("invalidContentCases")
    void parseAttemptReturnsExpectedErrorTypeForInvalidInput(String content,
                                                              String expectedType) {
        PlanParser parser = new PlanParser(new ObjectMapper());

        PlanParseAttemptResult result = parser.parseAttempt(content, null, Map.of());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(expectedType, result.getErrorType());
    }

    @ParameterizedTest
    @MethodSource("strictToolInputInvalidCases")
    void parseAttemptReturnsInvalidToolArgumentsWhenStrictModeAndToolInputInvalid(String content)
            throws Exception {
        PlanParser parser = new PlanParser(new ObjectMapper());
        Field field = PlanParser.class.getDeclaredField("strictToolArguments");
        field.setAccessible(true);
        field.set(parser, true);

        PlanParseAttemptResult result = parser.parseAttempt(content, null, Map.of());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(PlanParseErrorTypes.INVALID_TOOL_ARGUMENTS, result.getErrorType());
    }

    @Test
    void parseReturnsStepsAndSummaryWhenJsonValid() throws Exception {
        PlanParser parser = new PlanParser(new ObjectMapper());
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        Map<String, Object> context = new HashMap<>();
        context.put("k", "v");

        String json = """
                {
                  "summary": "ok",
                  "steps": [
                    {
                      "type": "TOOL",
                      "tool": "demo_tool",
                      "input": {
                        "arguments": {"query": "ping"}
                      }
                    }
                  ]
                }
                """;

        PlanParseResult result = parser.parse(json, request, context);

        assertNotNull(result);
        assertEquals("ok", result.getSummary());
        assertEquals(1, result.getSteps().size());
        assertEquals("TOOL", result.getSteps().get(0).getStepType());
        assertEquals("demo_tool", result.getSteps().get(0).getArguments().get(PlanningContextKeys.TOOL));
        assertEquals("ping", result.getSteps().get(0).getArguments().get(PlanningFieldKeys.QUERY));
    }

    @Test
    void parseReturnsNullWhenStepsMissing() throws Exception {
        PlanParser parser = new PlanParser(new ObjectMapper());

        PlanParseResult result = parser.parse("{\"summary\":\"x\"}", null, Map.of());

        assertNull(result);
    }

    @Test
    void parseReturnsNullWhenStrictToolArgumentsAndInputInvalid() throws Exception {
        PlanParser parser = new PlanParser(new ObjectMapper());
        Field field = PlanParser.class.getDeclaredField("strictToolArguments");
        field.setAccessible(true);
        field.set(parser, true);

        String json = """
                {
                  "summary": "ok",
                  "steps": [
                    {
                      "type": "TOOL",
                      "input": {
                        "query": "ping"
                      }
                    }
                  ]
                }
                """;

        PlanParseResult result = parser.parse(json, null, Map.of());

        assertNull(result);
        PlanParseAttemptResult attemptResult = parser.parseAttempt(json, null, Map.of());
        assertNotNull(attemptResult);
        assertFalse(attemptResult.isSuccess());
        assertEquals(PlanParseErrorTypes.INVALID_TOOL_ARGUMENTS, attemptResult.getErrorType());
    }

    @Test
    void parseAttemptReturnsMissingStepsWhenStepsMissing() {
        PlanParser parser = new PlanParser(new ObjectMapper());

        PlanParseAttemptResult result = parser.parseAttempt("{\"summary\":\"x\"}", null, Map.of());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(PlanParseErrorTypes.MISSING_STEPS, result.getErrorType());
    }

    @Test
    void parseAttemptReturnsJsonParseErrorWhenInvalidJson() {
        PlanParser parser = new PlanParser(new ObjectMapper());

        PlanParseAttemptResult result = parser.parseAttempt("not_json", null, Map.of());

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(PlanParseErrorTypes.JSON_PARSE_ERROR, result.getErrorType());
    }

    @Test
    void buildStepSpecExtractsContextDependsAndPolicy() {
        PlanParser parser = new PlanParser(new ObjectMapper());
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUERY, "q");
        input.put(PlanningFieldKeys.CONTEXT, Map.of("x", 1));
        input.put(PlanningFieldKeys.DEPENDS_ON, List.of("s1"));
        input.put(PlanningContextKeys.REQUIRES_APPROVAL, "true");
        input.put(PlanningContextKeys.APPROVAL_SOURCE, "evaluation");

        var step = parser.buildStepSpec("TOOL", input);

        assertEquals("TOOL", step.getStepType());
        assertEquals("q", step.getArguments().get(PlanningFieldKeys.QUERY));
        assertNotNull(step.getContext());
        assertEquals(1, step.getContext().get("x"));
        assertEquals(List.of("s1"), step.getDependsOn());
        assertTrue(Boolean.TRUE.equals(step.getRequiresApproval()));
        assertEquals("evaluation", step.getApprovalSource());
    }

    @Test
    void validateToolStepInputReturnsReasonByScenario() {
        PlanParser parser = new PlanParser(new ObjectMapper());

        assertEquals("input_empty", parser.validateToolStepInput(null));
        assertEquals("missing_tool_name", parser.validateToolStepInput(Map.of(PlanningFieldKeys.ARGUMENTS, Map.of("a", 1))));
        assertEquals("missing_arguments", parser.validateToolStepInput(Map.of(PlanningContextKeys.TOOL, "demo")));
        assertNull(parser.validateToolStepInput(Map.of(
                PlanningContextKeys.TOOL,
                "demo",
                PlanningFieldKeys.ARGUMENTS,
                Map.of("query", "q"))));
    }

    private static Stream<Arguments> invalidContentCases() {
        return Stream.of(
                Arguments.of("", PlanParseErrorTypes.EMPTY_OUTPUT),
                Arguments.of("   ", PlanParseErrorTypes.EMPTY_OUTPUT),
                Arguments.of("not_json", PlanParseErrorTypes.JSON_PARSE_ERROR),
                Arguments.of("{\"summary\":\"x\"}", PlanParseErrorTypes.MISSING_STEPS),
                Arguments.of("{\"steps\":[]}", PlanParseErrorTypes.NO_VALID_STEPS),
                Arguments.of("{\"steps\":[1]}",
                        PlanParseErrorTypes.NO_VALID_STEPS),
                Arguments.of("{\"steps\":[\"invalid\",true,null]}",
                        PlanParseErrorTypes.NO_VALID_STEPS)
        );
    }

    private static Stream<Arguments> strictToolInputInvalidCases() {
        return Stream.of(
                Arguments.of("""
                        {
                          "summary": "ok",
                          "steps": [
                            {
                              "type": "TOOL",
                              "input": {
                                "query": "ping"
                              }
                            }
                          ]
                        }
                        """),
                Arguments.of("""
                        {
                          "summary": "ok",
                          "steps": [
                            {
                              "type": "TOOL",
                              "tool": "demo",
                              "input": {
                                "query": "ping"
                              }
                            }
                          ]
                        }
                        """),
                Arguments.of("""
                        {
                          "summary": "ok",
                          "steps": [
                            {
                              "type": "TOOL",
                              "tool": "demo",
                              "input": {
                                "arguments": {}
                              }
                            }
                          ]
                        }
                        """)
        );
    }
}
