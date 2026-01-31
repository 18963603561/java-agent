package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextAssembler;
import com.example.agent.context.EvidencePackService;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.evaluation.CapabilityEvaluationProperties;
import com.example.agent.memory.MemoryRecallResult;
import com.example.agent.memory.MemoryRecallService;
import com.example.agent.memory.MemoryWriteService;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.planning.PlannerProperties;
import com.example.agent.planning.PlannerService;
import com.example.agent.reasoning.ChainOfThoughtService;
import com.example.agent.reasoning.DebateCoordinator;
import com.example.agent.reasoning.ThoughtNode;
import com.example.agent.reasoning.ThoughtTreeService;
import com.example.agent.reasoning.ThoughtTreeResult;
import com.example.agent.reflection.ReflectionReport;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.research.ResearchPipeline;
import com.example.agent.streaming.EventStreamService;
import com.example.agent.streaming.ContextEventPublisher;
import com.example.agent.context.ContextBuilder;
import com.example.agent.tools.hook.HookManager;
import com.example.agent.multiagent.MultiAgentCoordinator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        PlannerService plannerService = new PlannerService(
                Mockito.mock(ModelInvocationService.class),
                Mockito.mock(ModelToolResolver.class),
                promptAssembler,
                plannerProperties,
                evaluator,
                new ObjectMapper(),
                Mockito.mock(ContextAssembler.class),
                Mockito.mock(ContextEventPublisher.class), Mockito.mock(JsonOutputRepairService.class));

        ExecutionControlService executionControlService = new ExecutionControlService();
        StepRuntimeService stepRuntimeService = mock(StepRuntimeService.class);
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

        AgentRuntime runtime = new AgentRuntime(
                plannerService,
                reflectionService,
                stepRuntimeService,
                enforcementGateway,
                hookManager,
                executionControlService,
                thoughtTreeService,
                chainOfThoughtService,
                multiAgentCoordinator,
                debateCoordinator,
                researchPipeline,
                finalOutputService,
                reactLoopService,
                memoryRecallService,
                memoryWriteService,
                evidencePackService,
                contextBuilder,
                contextEventPublisher,
                eventPublisher,
                tracingPublisher,
                1,
                1,
                50,
                200,
                0.1
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
            // 娑撳秴顦╅悶?ApplicationEvent 閸掑棙鏁?
        }
    }
}
