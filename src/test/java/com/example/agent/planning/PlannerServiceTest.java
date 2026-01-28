package com.example.agent.planning;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextAssembler;
import com.example.agent.context.PromptAssemblyInput;
import com.example.agent.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.evaluation.CapabilityEvaluationProperties;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.model.PromptMessage;
import com.example.agent.model.PromptRole;
import com.example.agent.runtime.StepRequest;
import com.example.agent.streaming.ContextSnapshotStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

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
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver, promptAssembler,
                properties, evaluator, new ObjectMapper(), contextAssembler, contextEventPublisher);

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
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver, promptAssembler,
                properties, evaluator, new ObjectMapper(), contextAssembler, contextEventPublisher);

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

    @Test
    void disabledEvaluationDoesNotAffectPlanning() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver, promptAssembler,
                properties, evaluator, new ObjectMapper(), contextAssembler, contextEventPublisher);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"));
        assertEquals(1, plan.getSteps().size());
        assertEquals("TOOL", plan.getSteps().get(0).getStepType());
    }

    @Test
    void planPublishesPlanAssembledStageEvent() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = new PlannerService(modelInvocationService, modelToolResolver, promptAssembler,
                properties, evaluator, new ObjectMapper(), contextAssembler, contextEventPublisher);

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

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(List.of(new PromptMessage(PromptRole.USER, "hi")));
        bundle.setTruncatedSections(List.of("developer"));
        when(promptAssembler.build(any(), any(), any())).thenReturn(bundle);

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setBudgetUsedTokens(Map.of("total", 100));
        when(contextAssembler.assemble(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(input);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(new HashMap<>());

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        plannerService.plan(request, tenantContext, "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> sectionsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ContextSnapshotStage> stageCaptor = ArgumentCaptor.forClass(ContextSnapshotStage.class);
        Mockito.verify(contextEventPublisher).publishSnapshotStage(
                any(),
                eq("wf-1"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                sectionsCaptor.capture(),
                stageCaptor.capture(),
                any(),
                any());
        assertEquals(ContextSnapshotStage.PLAN_ASSEMBLED, stageCaptor.getValue());
        assertEquals(List.of("developer"), sectionsCaptor.getValue());
    }

    private CapabilityBoundaryEvaluator buildEvaluator(boolean enabled) {
        CapabilityEvaluationProperties evalProps = new CapabilityEvaluationProperties();
        evalProps.setEnabled(enabled);
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        com.example.agent.streaming.EventStreamService eventStreamService = Mockito.mock(
                com.example.agent.streaming.EventStreamService.class);
        return new CapabilityBoundaryEvaluator(evalProps, publisher, eventStreamService);
    }
}
