package com.example.agent.planning;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextAssembler;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.planning.approval.PlanningApprovalService;
import com.example.agent.planning.builder.HeuristicPlanBuilder;
import com.example.agent.planning.capability.PlanningCapabilityService;
import com.example.agent.planning.capability.PlanningRecommendationMapper;
import com.example.agent.planning.context.PlanningContextMapper;
import com.example.agent.planning.engine.LlmPlanEngine;
import com.example.agent.planning.parser.PlanParser;
import com.example.agent.planning.strategy.PlanningStrategyRegistry;
import com.example.agent.planning.strategy.handlers.ChainOfThoughtStrategyHandler;
import com.example.agent.planning.strategy.handlers.DebateStrategyHandler;
import com.example.agent.planning.strategy.handlers.DirectLlmStrategyHandler;
import com.example.agent.planning.strategy.handlers.MultiAgentStrategyHandler;
import com.example.agent.planning.strategy.handlers.ReactStrategyHandler;
import com.example.agent.planning.strategy.handlers.ResearchStrategyHandler;
import com.example.agent.planning.strategy.handlers.ThoughtTreeStrategyHandler;
import com.example.agent.planning.strategy.handlers.ToolFallbackStrategyHandler;
import com.example.agent.planning.telemetry.PlanTelemetry;
import com.example.agent.planning.telemetry.PlanningPromptTraceService;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.DefaultResourceLoader;

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
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"));
        assertNotNull(plan.getPlanId());
        assertFalse(plan.getSteps().isEmpty());
        assertEquals(1, plan.getSteps().size());
        StepSpec step = plan.getSteps().get(0);
        assertEquals("TOOL", step.getStepType());
        assertNotNull(step.getArguments());
        assertTrue(step.getArguments().containsKey("tool"));
    }

    @Test
    void planReturnsLlmStepWhenToolsDisabled() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setToolChoice(ModelToolChoice.none());
        request.setContext(new HashMap<>());

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"));
        assertNotNull(plan.getPlanId());
        assertFalse(plan.getSteps().isEmpty());
        assertEquals("LLM", plan.getSteps().get(0).getStepType());
    }

    @Test
    void planUsesLlmWhenEnabled() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

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
    void planPromptUsesContextSummary() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

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
        Map<String, Object> context = new HashMap<>();
        context.put("contextSnapshot", Map.of("large", "snapshot"));
        context.put("evidencePack", Map.of("items", List.of("a", "b")));
        context.put("tokenUsage", Map.of("total", 100));
        context.put("tool", "demo_tool");
        request.setContext(context);

        plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(requestCaptor.capture(), eq(ModelScene.PLANNER),
                any(TenantContext.class), eq("wf-1"), any(), eq("plan"), any());
        String prompt = requestCaptor.getValue().getPrompt();
        assertTrue(prompt.contains("contextSummary"));
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
    }

    @Test
    void disabledEvaluationDoesNotAffectPlanning() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

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
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                Mockito.mock(JsonOutputRepairService.class));

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

    @Test
    void planRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher,
                new ValidationSupport());
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                repairService);

        String badContent = "解释: {\"summary\":\"llm-plan\",\"steps\":[{\"type\":\"TOOL\",\"input\":{}}]} 后缀";
        String repaired = """
                {"summary":"llm-plan","steps":[{"type":"TOOL","input":{}}]}
                """;
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", badContent, 10, 20),
                        new ModelResponse("repair", repaired, 10, 20));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertEquals(1, plan.getSteps().size());
        assertEquals("TOOL", plan.getSteps().get(0).getStepType());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "planner");
        ArgumentCaptor<PromptTrace> traceCaptor = ArgumentCaptor.forClass(PromptTrace.class);
        Mockito.verify(modelInvocationService).recordPromptTrace(traceCaptor.capture(), any(TenantContext.class),
                eq("wf-1"), any(), eq("plan"), eq("planner"));
        PromptTrace trace = traceCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(trace.getRepairAttempted()));
        assertTrue(Boolean.TRUE.equals(trace.getParseSuccess()));
    }

    @Test
    void planFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);
        com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher = Mockito.mock(
                com.example.agent.streaming.payload.ContextEventPublisher.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher,
                new ValidationSupport());
        PlannerProperties properties = new PlannerProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        CapabilityBoundaryEvaluator evaluator = buildEvaluator(false);
        PlannerService plannerService = newPlannerService(modelInvocationService,
                promptAssembler,
                contextAssembler,
                contextEventPublisher,
                properties,
                evaluator,
                repairService);

        String badContent = "无法解析的输出";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("planner", badContent, 10, 20),
                        new ModelResponse("repair", "", 10, 20));

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        PlanResult plan = plannerService.plan(request, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertEquals(1, plan.getSteps().size());
        assertEquals("TOOL", plan.getSteps().get(0).getStepType());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "planner");
        ArgumentCaptor<PromptTrace> traceCaptor = ArgumentCaptor.forClass(PromptTrace.class);
        Mockito.verify(modelInvocationService).recordPromptTrace(traceCaptor.capture(), any(TenantContext.class),
                eq("wf-1"), any(), eq("plan"), eq("planner"));
        PromptTrace trace = traceCaptor.getValue();
        assertFalse(Boolean.TRUE.equals(trace.getParseSuccess()));
        assertEquals("json_parse_error", trace.getParseErrorType());
        assertTrue(Boolean.TRUE.equals(trace.getRepairAttempted()));
        assertFalse(Boolean.TRUE.equals(trace.getRepairSuccess()));
    }

    private PlannerService newPlannerService(ModelInvocationService modelInvocationService,
                                             PromptAssembler promptAssembler,
                                             ContextAssembler contextAssembler,
                                             com.example.agent.streaming.payload.ContextEventPublisher contextEventPublisher,
                                             PlannerProperties properties,
                                             CapabilityBoundaryEvaluator evaluator,
                                             JsonOutputRepairService repairService) {
        ObjectMapper objectMapper = new ObjectMapper();
        PlanParser planParser = new PlanParser(objectMapper);
        PlanningPromptTraceService planningPromptTraceService = new PlanningPromptTraceService(modelInvocationService);
        PlanTelemetry planTelemetry = new PlanTelemetry(promptAssembler, contextAssembler, contextEventPublisher,
                planningPromptTraceService);
        LlmPlanEngine llmPlanEngine = new LlmPlanEngine(modelInvocationService,
                Mockito.mock(com.example.agent.capabilities.llm.tooling.ModelToolResolver.class),
                new PlanningPromptBuilder(objectMapper,
                        new DefaultResourceLoader(),
                        "classpath:prompts/planning/planner-plan-prompt.md"),
                repairService,
                objectMapper,
                planParser,
                planTelemetry);
        PlanningStrategyRegistry strategyRegistry = new PlanningStrategyRegistry(List.of(
                new ChainOfThoughtStrategyHandler(planParser),
                new ThoughtTreeStrategyHandler(planParser),
                new MultiAgentStrategyHandler(planParser),
                new DebateStrategyHandler(planParser),
                new ResearchStrategyHandler(planParser),
                new ReactStrategyHandler(planParser),
                new DirectLlmStrategyHandler(planParser),
                new ToolFallbackStrategyHandler(planParser)
        ));
        HeuristicPlanBuilder heuristicPlanBuilder = new HeuristicPlanBuilder(strategyRegistry);
        PlanningCapabilityService planningCapabilityService = new PlanningCapabilityService(
                evaluator,
                heuristicPlanBuilder,
                new PlanningRecommendationMapper());
        return new PlannerService(properties,
                planningCapabilityService,
                new PlanningApprovalService(),
                llmPlanEngine,
                heuristicPlanBuilder,
                new PlanningContextMapper());
    }

    private CapabilityBoundaryEvaluator buildEvaluator(boolean enabled) {
        CapabilityEvaluationProperties evalProps = new CapabilityEvaluationProperties();
        evalProps.setEnabled(enabled);
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        com.example.agent.streaming.sse.EventStreamService eventStreamService = Mockito.mock(
                com.example.agent.streaming.sse.EventStreamService.class);
        return new CapabilityBoundaryEvaluator(evalProps, publisher, eventStreamService);
    }
}
