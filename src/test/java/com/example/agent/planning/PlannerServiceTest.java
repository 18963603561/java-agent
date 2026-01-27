package com.example.agent.planning;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.runtime.StepRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PlannerServiceTest {

    @Test
    void planReturnsToolStepForSimpleQueryWithFallback() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver,
                properties, new ObjectMapper());

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"));
        assertNotNull(plan.getPlanId());
        assertFalse(plan.getSteps().isEmpty());
        assertEquals(1, plan.getSteps().size());
        StepRequest step = plan.getSteps().get(0);
        assertEquals("TOOL", step.getStepType());
        assertTrue(step.getInput().containsKey("tool"));
    }

    @Test
    void planUsesLlmWhenEnabled() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver,
                properties, new ObjectMapper());

        String content = """
                {
                  "summary":"llm-plan",
                  "steps":[
                    {"type":"TOOL","tool":"demo_tool","input":{"query":"ping","context":{}}}
                  ]
                }
                """;
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.PLANNER),
                any(TenantContext.class), any(), any(), eq("plan"), any()))
                .thenReturn(new ModelResponse("planner", content, 10, 20));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertEquals(1, plan.getSteps().size());
        assertEquals("TOOL", plan.getSteps().get(0).getStepType());
    }
}
