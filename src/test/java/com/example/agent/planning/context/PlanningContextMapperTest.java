package com.example.agent.planning.context;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.planning.PlanningContextKeys;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningContextMapperTest {

    private final PlanningContextMapper mapper = new PlanningContextMapper();

    @Test
    void fromTaskRequestUsesRequestToolChoiceWhenContextMissing() {
        TaskRequest request = new TaskRequest();
        request.setToolChoice(ModelToolChoice.required());

        PlanningContext context = mapper.fromTaskRequest(request);

        assertNotNull(context);
        assertNotNull(context.getToolChoice());
        assertEquals(ModelToolChoice.Mode.REQUIRED, context.getToolChoice().getMode());
    }

    @Test
    void fromTaskRequestKeepsContextToolChoiceAndNormalizeString() {
        TaskRequest request = new TaskRequest();
        request.setToolChoice(ModelToolChoice.none());
        request.setContext(new HashMap<>(Map.of(PlanningContextKeys.TOOL_CHOICE, "specified:demo_tool")));

        PlanningContext context = mapper.fromTaskRequest(request);

        assertNotNull(context.getToolChoice());
        assertEquals(ModelToolChoice.Mode.SPECIFIED, context.getToolChoice().getMode());
        assertEquals("demo_tool", context.getToolChoice().getToolName());
    }

    @Test
    void fromTaskRequestRemovesInvalidToolChoice() {
        TaskRequest request = new TaskRequest();
        request.setContext(new HashMap<>(Map.of(PlanningContextKeys.TOOL_CHOICE, "illegal_mode")));

        PlanningContext context = mapper.fromTaskRequest(request);

        assertNull(context.getToolChoice());
        assertFalse(context.containsKey(PlanningContextKeys.TOOL_CHOICE));
    }

    @Test
    void fromTaskRequestCopiesContextWithoutSideEffect() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("x", 1);
        TaskRequest request = new TaskRequest();
        request.setContext(raw);

        PlanningContext context = mapper.fromTaskRequest(request);
        context.put("x", 2);

        assertEquals(1, raw.get("x"));
        assertEquals(2, context.get("x"));
    }

    @Test
    void fromTaskRequestHandlesNullRequest() {
        PlanningContext context = mapper.fromTaskRequest(null);

        assertNotNull(context);
        assertTrue(context.mutableValues().isEmpty());
    }
}

