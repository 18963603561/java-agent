package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.common.error.ErrorCodeProvider;
import com.example.agent.reflection.ReflectionReport;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.runtime.control.RuntimeApprovalGate;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.engine.RuntimeContextUpdateService;
import com.example.agent.runtime.engine.RuntimeEventDispatchService;
import com.example.agent.runtime.engine.StepExecutionCoordinator;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryProperties;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepExecutionCoordinatorTest {

    @Test
    void executeStepReturnsSuccessWhenExecutionSucceeds() {
        StepRuntimeService stepRuntimeService = mock(StepRuntimeService.class);
        StepRecord record = new StepRecord();
        record.setStepId("step-1");
        record.setType("TOOL");
        record.setAttempt(1);
        record.setStatus(StepState.STARTED);
        when(stepRuntimeService.startStep(any(), any(), anyInt(), any(), any(), any())).thenReturn(record);
        when(stepRuntimeService.completeStep(any(), any(), any())).thenAnswer(invocation -> {
            StepRecord completed = invocation.getArgument(0);
            StepResult stepResult = new StepResult();
            completed.setOutput(stepResult);
            completed.setStatus(StepState.COMPLETED);
            return completed;
        });

        StepOutputSummaryBuilder summaryBuilder = mock(StepOutputSummaryBuilder.class);
        when(summaryBuilder.isEnabled()).thenReturn(false);

        RuntimeExecutionGate runtimeExecutionGate = mock(RuntimeExecutionGate.class);
        RuntimeApprovalGate runtimeApprovalGate = mock(RuntimeApprovalGate.class);
        StepExecutionDelegate stepExecutionDelegate = mock(StepExecutionDelegate.class);
        when(stepExecutionDelegate.execute(any())).thenReturn(StepExecutionOutput.fromPayload(Map.of("answer", "ok")));
        HookManager hookManager = mock(HookManager.class);

        StepFailureRecoveryService failureRecoveryService = new StepFailureRecoveryService(stepExecutionDelegate, 1, 1);
        RetryPolicy retryPolicy = new RetryPolicy(1, 2, 0.0);
        ReflectionService reflectionService = mock(ReflectionService.class);
        when(reflectionService.reflect(any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new ReflectionResult(false, new ReflectionReport(0.8, "ok")));

        TestEventPublisher eventPublisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        Mockito.when(tracingPublisher.currentTraceId()).thenReturn("trace-1");
        RuntimeEventDispatchService eventDispatchService = new RuntimeEventDispatchService(eventPublisher, tracingPublisher);

        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService contextUpdateService = new RuntimeContextUpdateService(rawOutputEnvelopeBuilder);

        StepExecutionCoordinator coordinator = new StepExecutionCoordinator(
                stepRuntimeService,
                summaryBuilder,
                runtimeExecutionGate,
                runtimeApprovalGate,
                stepExecutionDelegate,
                hookManager,
                failureRecoveryService,
                retryPolicy,
                reflectionService,
                eventDispatchService,
                contextUpdateService
        );

        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        RuntimeContext runtimeContext = new RuntimeContext();
        List<StepResult> stepOutputs = new ArrayList<>();
        StepExecutionCoordinator.StepExecutionResult result = coordinator.executeStep(
                step,
                new TaskRequest(),
                new TenantContext("t-1", "u-1", List.of(), "req-1", "trace-1"),
                "wf-1",
                "task-1",
                new AtomicLong(0),
                runtimeContext,
                0,
                stepOutputs
        );

        assertEquals(StepExecutionCoordinator.StepExecutionResult.SUCCESS, result);
        assertEquals(1, stepOutputs.size());
        assertTrue(eventPublisher.events.stream().anyMatch(e -> e.getType() == EventType.REFLECTION_STARTED));
        assertTrue(eventPublisher.events.stream().anyMatch(e -> e.getType() == EventType.REFLECTION_COMPLETED));
    }

    @Test
    void executeStepReturnsReplanWhenRecoverySuggestsReplan() {
        StepRuntimeService stepRuntimeService = mock(StepRuntimeService.class);
        StepRecord record = new StepRecord();
        record.setStepId("step-1");
        record.setType("TOOL");
        record.setAttempt(1);
        record.setStatus(StepState.STARTED);
        when(stepRuntimeService.startStep(any(), any(), anyInt(), any(), any(), any())).thenReturn(record);

        StepOutputSummaryBuilder summaryBuilder = mock(StepOutputSummaryBuilder.class);
        when(summaryBuilder.isEnabled()).thenReturn(false);

        RuntimeExecutionGate runtimeExecutionGate = mock(RuntimeExecutionGate.class);
        RuntimeApprovalGate runtimeApprovalGate = mock(RuntimeApprovalGate.class);
        StepExecutionDelegate stepExecutionDelegate = mock(StepExecutionDelegate.class);
        when(stepExecutionDelegate.execute(any())).thenThrow(new TestError("BUDGET_EXCEEDED", "decompose_needed"));
        HookManager hookManager = mock(HookManager.class);

        StepFailureRecoveryService failureRecoveryService = new StepFailureRecoveryService(stepExecutionDelegate, 0, 2);
        RetryPolicy retryPolicy = new RetryPolicy(1, 2, 0.0);
        ReflectionService reflectionService = mock(ReflectionService.class);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        Mockito.when(tracingPublisher.currentTraceId()).thenReturn("trace-1");
        RuntimeEventDispatchService eventDispatchService = new RuntimeEventDispatchService(eventPublisher, tracingPublisher);

        StepSummaryProperties properties = new StepSummaryProperties();
        RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder = new RawOutputEnvelopeBuilder(properties);
        RuntimeContextUpdateService contextUpdateService = new RuntimeContextUpdateService(rawOutputEnvelopeBuilder);

        StepExecutionCoordinator coordinator = new StepExecutionCoordinator(
                stepRuntimeService,
                summaryBuilder,
                runtimeExecutionGate,
                runtimeApprovalGate,
                stepExecutionDelegate,
                hookManager,
                failureRecoveryService,
                retryPolicy,
                reflectionService,
                eventDispatchService,
                contextUpdateService
        );

        StepSpec step = new StepSpec("TOOL", Map.of("tool", "search"));
        StepExecutionCoordinator.StepExecutionResult result = coordinator.executeStep(
                step,
                new TaskRequest(),
                new TenantContext("t-1", "u-1", List.of(), "req-1", "trace-1"),
                "wf-1",
                "task-1",
                new AtomicLong(0),
                new RuntimeContext(),
                0,
                new ArrayList<>()
        );

        assertNotNull(result);
        assertEquals(StepExecutionCoordinator.StepExecutionResult.REPLAN, result);
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
        }
    }

    private static class TestError extends RuntimeException implements ErrorCodeProvider {

        private final String errorCode;

        private TestError(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        @Override
        public String getErrorCode() {
            return errorCode;
        }
    }
}

