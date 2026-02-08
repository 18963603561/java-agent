package com.example.agent.planning.parser;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanParserTest {

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
}

