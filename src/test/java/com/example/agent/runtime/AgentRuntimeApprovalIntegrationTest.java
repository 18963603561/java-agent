package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextAssembler;
import com.example.agent.capabilities.context.ContextBuilder;
import com.example.agent.capabilities.context.EvidencePackService;
import com.example.agent.capabilities.context.research.ResearchPipeline;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.memory.MemoryRecallResult;
import com.example.agent.capabilities.memory.MemoryRecallService;
import com.example.agent.capabilities.memory.MemoryWriteService;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.capabilities.tools.validation.ToolArgumentValidatorRuntime;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.orchestration.multiagent.MultiAgentCoordinator;
import com.example.agent.planning.PlannerProperties;
import com.example.agent.planning.PlannerService;
import com.example.agent.planning.PlanningPromptBuilder;
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
import com.example.agent.reasoning.cot.ChainOfThoughtService;
import com.example.agent.reasoning.debate.DebateCoordinator;
import com.example.agent.reasoning.thoughttree.ThoughtNode;
import com.example.agent.reasoning.thoughttree.ThoughtTreeResult;
import com.example.agent.reasoning.thoughttree.ThoughtTreeService;
import com.example.agent.reflection.ReflectionReport;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.runtime.control.ExecutionControlService;
import com.example.agent.runtime.control.ExecutionControlState;
import com.example.agent.runtime.control.RuntimeApprovalGate;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.engine.AgentRuntime;
import com.example.agent.runtime.engine.RuntimeContextUpdateService;
import com.example.agent.runtime.engine.RuntimeEventDispatchService;
import com.example.agent.runtime.engine.StepExecutionCoordinator;
import com.example.agent.runtime.finalize.RuntimeFinalizationService;
import com.example.agent.runtime.llm.LlmStepService;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.output.FinalOutputService;
import com.example.agent.runtime.prepare.RuntimePreparationService;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.react.ReactLoopService;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeApprovalIntegrationTest {

    @Test
    void evaluationApprovalBlocksRuntime() throws Exception {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        CapabilityEvaluationProperties evalProps = new CapabilityEvaluationProperties();
        evalProps.setEnabled(true);
        evalProps.setRiskThreshold(0.1);
        evalProps.setComplexityThreshold(0.1);
        evalProps.setForceApprovalAboveRisk(true);
        CapabilityBoundaryEvaluator evaluator = new CapabilityBoundaryEvaluator(
                evalProps,
                eventPublisher,
                Mockito.mock(EventStreamService.class));

        PlannerProperties plannerProperties = new PlannerProperties();
        plannerProperties.setLlmEnabled(false);
        plannerProperties.setFallbackEnabled(true);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ContextAssembler plannerContextAssembler = Mockito.mock(ContextAssembler.class);
        ContextEventPublisher plannerContextEventPublisher = Mockito.mock(ContextEventPublisher.class);
        ObjectMapper plannerObjectMapper = new ObjectMapper();
        PlanParser planParser = new PlanParser(plannerObjectMapper);
        PlanningPromptTraceService planningPromptTraceService = new PlanningPromptTraceService(modelInvocationService);
        PlanTelemetry planTelemetry = new PlanTelemetry(promptAssembler,
                plannerContextAssembler,
                plannerContextEventPublisher,
                planningPromptTraceService);
        LlmPlanEngine llmPlanEngine = new LlmPlanEngine(modelInvocationService,
                Mockito.mock(com.example.agent.capabilities.llm.tooling.ModelToolResolver.class),
                new PlanningPromptBuilder(plannerObjectMapper,
                        new DefaultResourceLoader(),
                        "classpath:prompts/planning/planner-plan-prompt.md"),
                Mockito.mock(JsonOutputRepairService.class),
                plannerObjectMapper,
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
        PlannerService plannerService = new PlannerService(
                plannerProperties,
                planningCapabilityService,
                new PlanningApprovalService(),
                llmPlanEngine,
                heuristicPlanBuilder,
                new PlanningContextMapper());

        ExecutionControlService executionControlService = new ExecutionControlService();
        StepRuntimeService stepRuntimeService = mock(StepRuntimeService.class);
        StepOutputSummaryBuilder reflectionSummaryBuilder = mock(StepOutputSummaryBuilder.class);
        RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder = mock(RawOutputEnvelopeBuilder.class);
        when(reflectionSummaryBuilder.isEnabled()).thenReturn(false);
        StepRecord record = new StepRecord();
        record.setStepId("step-1");
        record.setWorkflowId("wf-1");
        record.setType("TOOL");
        record.setStatus(StepState.STARTED);
        record.setAttempt(1);
        when(stepRuntimeService.startStep(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(record);
        when(stepRuntimeService.completeStep(any(), any(), any()))
                .thenReturn(record);

        ReflectionService reflectionService = mock(ReflectionService.class);
        when(reflectionService.reflect(any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new ReflectionResult(false, new ReflectionReport(0.8, "ok")));

        EnforcementGateway enforcementGateway = mock(EnforcementGateway.class);
        when(enforcementGateway.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("ok", true));

        HookManager hookManager = mock(HookManager.class);
        ThoughtTreeService thoughtTreeService = mock(ThoughtTreeService.class);
        ThoughtTreeResult treeResult = new ThoughtTreeResult();
        ThoughtNode rootNode = new ThoughtNode();
        rootNode.setNodeId("root");
        rootNode.setDepth(0);
        rootNode.setScore(0.5);
        treeResult.setRoot(rootNode);
        treeResult.setBestSolution("ok");
        treeResult.setConfidence(0.6);
        treeResult.setTotalThoughts(1);
        treeResult.setTreeDepth(1);
        when(thoughtTreeService.buildTree(any(), any())).thenReturn(treeResult);
        ChainOfThoughtService chainOfThoughtService = mock(ChainOfThoughtService.class);
        MultiAgentCoordinator multiAgentCoordinator = mock(MultiAgentCoordinator.class);
        DebateCoordinator debateCoordinator = mock(DebateCoordinator.class);
        ResearchPipeline researchPipeline = mock(ResearchPipeline.class);
        FinalOutputService finalOutputService = mock(FinalOutputService.class);
        LlmStepService llmStepService = mock(LlmStepService.class);
        ToolArgumentValidatorRuntime toolArgumentValidator = mock(ToolArgumentValidatorRuntime.class);
        when(llmStepService.run(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("answer", "ok"));
        when(finalOutputService.finalizeOutput(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("answer", "ok"));
        ReactLoopService reactLoopService = mock(ReactLoopService.class);
        MemoryRecallService memoryRecallService = mock(MemoryRecallService.class);
        when(memoryRecallService.recall(any(), any(), any()))
                .thenReturn(MemoryRecallResult.skipped("skip"));
        MemoryWriteService memoryWriteService = mock(MemoryWriteService.class);
        EvidencePackService evidencePackService = mock(EvidencePackService.class);
        TracingPublisher tracingPublisher = mock(TracingPublisher.class);
        when(tracingPublisher.currentTraceId()).thenReturn("trace");
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        ContextEventPublisher contextEventPublisher = mock(ContextEventPublisher.class);

        RuntimeExecutionGate runtimeExecutionGate = new RuntimeExecutionGate(executionControlService);
        RuntimeApprovalGate runtimeApprovalGate = new RuntimeApprovalGate(executionControlService);
        StepExecutionDelegate stepExecutionDelegate = mock(StepExecutionDelegate.class);
        when(stepExecutionDelegate.execute(any())).thenReturn(StepExecutionOutput.fromPayload(Map.of("answer", "ok")));

        RuntimePreparationService runtimePreparationService = new RuntimePreparationService(
                memoryRecallService,
                hookManager,
                evidencePackService,
                contextBuilder,
                contextEventPublisher
        );
        RuntimeFinalizationService runtimeFinalizationService = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService
        );
        StepFailureRecoveryService stepFailureRecoveryService = new StepFailureRecoveryService(stepExecutionDelegate, 1, 1);
        RetryPolicy retryPolicy = new RetryPolicy(50L, 200L, 0.1);
        RuntimeEventDispatchService runtimeEventDispatchService = new RuntimeEventDispatchService(
                eventPublisher,
                tracingPublisher
        );
        RuntimeContextUpdateService runtimeContextUpdateService = new RuntimeContextUpdateService(rawOutputEnvelopeBuilder);
        StepExecutionCoordinator stepExecutionCoordinator = new StepExecutionCoordinator(
                stepRuntimeService,
                reflectionSummaryBuilder,
                runtimeExecutionGate,
                runtimeApprovalGate,
                stepExecutionDelegate,
                hookManager,
                stepFailureRecoveryService,
                retryPolicy,
                reflectionService,
                runtimeEventDispatchService,
                runtimeContextUpdateService
        );

        AgentRuntime runtime = new AgentRuntime(
                plannerService,
                runtimePreparationService,
                runtimeFinalizationService,
                runtimeEventDispatchService,
                stepExecutionCoordinator
        );

        TaskRequest request = new TaskRequest();
        request.setQuery("test");
        request.setIdempotencyKey("id-1");
        request.setContext(Map.of("tool", "demo_tool"));

        TenantContext tenantContext = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        AtomicLong seqCounter = new AtomicLong(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<RuntimeResult> future = executor.submit(() ->
                runtime.run(request, tenantContext, "wf-1", "task-1", seqCounter));
        try {
            waitForApprovalState(executionControlService, "wf-1", ExecutionControlState.WAIT_APPROVAL, 2000);

            StreamEvent approvalEvent = eventPublisher.events.stream()
                    .filter(event -> event.getType() == EventType.APPROVAL_REQUESTED)
                    .findFirst()
                    .orElse(null);
            assertNotNull(approvalEvent);
            assertEquals("evaluation", approvalEvent.getPayload().get("approvalSource"));
            assertEquals("PENDING_APPROVAL", approvalEvent.getPayload().get("status"));

            verify(stepRuntimeService, never()).startStep(any(), any(), anyInt(), any(), any(), any());
            verify(enforcementGateway, never()).execute(any(), any(), any(), any(), any(), any());

            executionControlService.decideApproval("wf-1", "approve");
            RuntimeResult result = future.get(2, TimeUnit.SECONDS);
            assertNotNull(result);
            verify(stepRuntimeService, atLeastOnce()).startStep(any(), any(), anyInt(), any(), any(), any());
        } finally {
            executionControlService.decideApproval("wf-1", "approve");
            future.cancel(true);
            executor.shutdownNow();
        }
    }

    private void waitForApprovalState(ExecutionControlService service,
                                      String workflowId,
                                      ExecutionControlState expected,
                                      long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (service.getState(workflowId) == expected) {
                return;
            }
            Thread.sleep(20);
        }
        fail("wait_for_approval_timeout");
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        private final List<StreamEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            // 兼容 ApplicationEvent 发布方法
        }
    }
}
