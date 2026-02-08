package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.capabilities.llm.tooling.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolDefinition;
import com.example.agent.runtime.llm.LlmDecisionService;
import com.example.agent.security.auth.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmDecisionServiceTest {

    @Test
    void buildDecisionContextIncludesToolsAndRuntime() {
        LlmDecisionService service = new LlmDecisionService(new ObjectMapper());

        ModelRequest decisionRequest = new ModelRequest();
        decisionRequest.setToolChoice(ModelToolChoice.specified("tool-a"));
        ModelToolDefinition toolDefinition = new ModelToolDefinition();
        toolDefinition.setName("tool-a");
        toolDefinition.setDescription("desc");
        decisionRequest.setTools(List.of(toolDefinition));

        TaskRequest request = new TaskRequest();
        request.setSessionId("session-1");
        Map<String, Object> stepInput = Map.of(
                "steps", List.of(Map.of("stepId", "s1", "type", "TOOL", "status", "COMPLETED", "answer", "ok")),
                "lastStepSummary", Map.of("summary", "last")
        );
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");

        Map<String, Object> context = service.buildDecisionContext(
                "query",
                decisionRequest,
                request,
                stepInput,
                tenantContext,
                false
        );

        assertEquals("query", context.get("query"));
        assertTrue(context.containsKey("steps"));
        assertTrue(context.containsKey("availableTools"));
        assertTrue(context.containsKey("constraints"));
        assertTrue(context.containsKey("runtime"));

        Object runtimeObj = context.get("runtime");
        assertTrue(runtimeObj instanceof Map<?, ?>);
        Map<?, ?> runtime = (Map<?, ?>) runtimeObj;
        assertEquals("tenant-1", runtime.get("tenantId"));
        assertEquals("trace-1", runtime.get("traceId"));
        assertEquals("session-1", runtime.get("sessionId"));

        Object constraintsObj = context.get("constraints");
        assertTrue(constraintsObj instanceof Map<?, ?>);
        Map<?, ?> constraints = (Map<?, ?>) constraintsObj;
        assertEquals(false, constraints.get("disableTools"));
    }

    @Test
    void parseJsonMapAndReadersWorkWithFallback() {
        LlmDecisionService service = new LlmDecisionService(new ObjectMapper());

        Map<String, Object> parsed = service.parseJsonMap("{\"mode\":\"answer\",\"confidence\":0.8}");
        assertEquals("answer", service.readString(parsed, "mode"));
        assertEquals(0.8, service.readNumber(parsed, "confidence", 0.1));

        Map<String, Object> empty = service.parseJsonMap("not_json");
        assertTrue(empty.isEmpty());
        assertEquals(0.3, service.readNumber(empty, "missing", 0.3));

        Map<String, Object> map = service.readMap(Map.of("a", 1));
        assertEquals(1, map.get("a"));
    }

    @Test
    void buildDecisionPromptContainsContextMarker() {
        LlmDecisionService service = new LlmDecisionService(new ObjectMapper());

        String prompt = service.buildDecisionPrompt("{}");

        assertNotNull(prompt);
        assertTrue(prompt.contains("LLM_STEP_CONTEXT_JSON"));
        assertFalse(prompt.isBlank());
    }
}
