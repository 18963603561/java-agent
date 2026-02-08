package com.example.agent.planning.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.planning.PlanningPromptBuilder;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.parser.PlanParseResult;
import com.example.agent.planning.parser.PlanParser;
import com.example.agent.planning.telemetry.PlanTelemetry;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class LlmPlanEngineTest {

    @Test
    void executeReturnsPlanWhenParserSuccess() throws Exception {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PlanningPromptBuilder planningPromptBuilder = Mockito.mock(PlanningPromptBuilder.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        PlanParser planParser = Mockito.mock(PlanParser.class);
        PlanTelemetry planTelemetry = Mockito.mock(PlanTelemetry.class);

        when(planningPromptBuilder.buildPrompt(any(), anyMap())).thenReturn("prompt");
        when(modelInvocationService.invoke(any(ModelRequest.class),
                eq(ModelScene.PLANNER),
                any(),
                any(),
                any(),
                eq("plan"),
                anyMap()))
                .thenReturn(new ModelResponse("planner-model", "{}", 8, 6));

        StepSpec step = new StepSpec();
        step.setStepType("TOOL");
        when(planParser.parse(any(), any(), anyMap())).thenReturn(new PlanParseResult("llm-summary", List.of(step)));

        LlmPlanEngine engine = new LlmPlanEngine(modelInvocationService,
                modelToolResolver,
                planningPromptBuilder,
                repairService,
                new ObjectMapper(),
                planParser,
                planTelemetry);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        LlmPlanEngineResult result = engine.execute(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0),
                new PlanningContext(new HashMap<>()),
                "plan-1");

        assertTrue(result.isSuccess());
        assertNotNull(result.getPlanResult());
        assertEquals("llm-summary", result.getPlanResult().getSummary());
        assertEquals(1, result.getPlanResult().getSteps().size());
        Mockito.verify(planTelemetry).recordPromptTrace(anyMap(),
                eq("prompt"),
                any(),
                eq("wf-1"),
                any(),
                eq("planner-model"),
                eq(true),
                eq(null),
                eq(false),
                eq(false));
    }

    @Test
    void executeUsesRepairWhenInitialParseFailed() throws Exception {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PlanningPromptBuilder planningPromptBuilder = Mockito.mock(PlanningPromptBuilder.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        PlanParser planParser = Mockito.mock(PlanParser.class);
        PlanTelemetry planTelemetry = Mockito.mock(PlanTelemetry.class);

        when(planningPromptBuilder.buildPrompt(any(), anyMap())).thenReturn("prompt");
        when(modelInvocationService.invoke(any(ModelRequest.class),
                eq(ModelScene.PLANNER),
                any(),
                any(),
                any(),
                eq("plan"),
                anyMap()))
                .thenReturn(new ModelResponse("planner-model", "bad-json", 12, 10));

        StepSpec repairedStep = new StepSpec();
        repairedStep.setStepType("TOOL");
        when(planParser.parse(any(), any(), anyMap()))
                .thenThrow(new RuntimeException("parse-fail"))
                .thenReturn(new PlanParseResult("repair-summary", List.of(repairedStep)));
        when(repairService.repair(eq(PlanningFieldKeys.SCENE_PLANNER), any(), any(), any(), eq(1)))
                .thenReturn("{\"summary\":\"repair-summary\",\"steps\":[{\"type\":\"TOOL\",\"input\":{\"tool\":\"demo\",\"arguments\":{\"query\":\"q\"}}}]}");

        LlmPlanEngine engine = new LlmPlanEngine(modelInvocationService,
                modelToolResolver,
                planningPromptBuilder,
                repairService,
                new ObjectMapper(),
                planParser,
                planTelemetry);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        LlmPlanEngineResult result = engine.execute(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0),
                new PlanningContext(new HashMap<>()),
                "plan-2");

        assertTrue(result.isSuccess());
        assertEquals("repair-summary", result.getPlanResult().getSummary());
        Mockito.verify(planTelemetry).recordPromptTrace(anyMap(),
                eq("prompt"),
                any(),
                eq("wf-1"),
                any(),
                eq("planner-model"),
                eq(true),
                eq(null),
                eq(true),
                eq(true));
    }

    @Test
    void executeReturnsFailureWhenModelOutputEmpty() throws Exception {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PlanningPromptBuilder planningPromptBuilder = Mockito.mock(PlanningPromptBuilder.class);
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        PlanParser planParser = Mockito.mock(PlanParser.class);
        PlanTelemetry planTelemetry = Mockito.mock(PlanTelemetry.class);

        when(planningPromptBuilder.buildPrompt(any(), anyMap())).thenReturn("prompt");
        when(modelInvocationService.invoke(any(ModelRequest.class),
                eq(ModelScene.PLANNER),
                any(),
                any(),
                any(),
                eq("plan"),
                anyMap()))
                .thenReturn(new ModelResponse("planner-model", "   ", 3, 1));

        LlmPlanEngine engine = new LlmPlanEngine(modelInvocationService,
                modelToolResolver,
                planningPromptBuilder,
                repairService,
                new ObjectMapper(),
                planParser,
                planTelemetry);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        LlmPlanEngineResult result = engine.execute(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0),
                new PlanningContext(new HashMap<>()),
                "plan-3");

        assertFalse(result.isSuccess());
        Mockito.verify(planParser, never()).parse(any(), any(), anyMap());
    }
}
