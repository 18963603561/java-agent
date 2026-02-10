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
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepExecutionCoordinatorReflectionFailureTest {

    @Test
    void executeStepMarksReflectionRetryWhenReflectionRequestsRetry() {
        TestCoordinatorFixture fixture = createBaseFixture();
        when(fixture.reflectionService.reflect(any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new ReflectionResult(true, new ReflectionReport(0.2, "need_retry")))
                .thenReturn(new ReflectionResult(false, new ReflectionReport(0.9, "ok")));

        StepExecutionCoordinator.StepExecutionResult result = fixture.coordinator.executeStep(
                new StepSpec("TOOL", Map.of("tool", "search")),
                new TaskRequest(),
                new TenantContext("t-1", "u-1", List.of(), "req-1", "trace-1"),
                "wf-1",
                "task-1",
                new AtomicLong(0),
                new RuntimeContext(),
                0,
                new ArrayList<>()
        );

        assertEquals(StepExecutionCoordinator.StepExecutionResult.SUCCESS, result);
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.stepRuntimeService, Mockito.atLeastOnce())
                .failStep(any(), errorCodeCaptor.capture(), any(), any());
        org.junit.jupiter.api.Assertions.assertTrue(errorCodeCaptor.getAllValues().contains("REFLECTION_RETRY"));
    }

    @Test
    void executeStepFailsWithInternalErrorWhenReflectionThrowsAndRecoveryStops() {
        TestCoordinatorFixture fixture = createBaseFixture();
        when(fixture.reflectionService.reflect(any(), any(), any(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("reflection_down"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> fixture.coordinator.executeStep(
                new StepSpec("TOOL", Map.of("tool", "search")),
                new TaskRequest(),
                new TenantContext("t-1", "u-1", List.of(), "req-1", "trace-1"),
                "wf-1",
                "task-1",
                new AtomicLong(0),
                new RuntimeContext(),
                0,
                new ArrayList<>()
        ));

        assertEquals("reflection_down", exception.getMessage());
        verify(fixture.stepRuntimeService).failStep(any(), eq("INTERNAL_ERROR"), any(), any());
    }

    @Test
    void executeStepUsesErrorCodeFromRecoveryErrorWhenProvided() {
        TestCoordinatorFixture fixture = createBaseFixture();
        when(fixture.reflectionService.reflect(any(), any(), any(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("reflection_down"));

        StepFailureRecoveryService customRecovery = new StepFailureRecoveryService(
                fixture.stepExecutionDelegate,
                new com.example.agent.runtime.recovery.FailureClassifier() {
                    @Override
                    public com.example.agent.runtime.recovery.FailureType classify(Throwable throwable) {
                        return com.example.agent.runtime.recovery.FailureType.NON_RETRYABLE;
                    }
                },
                new com.example.agent.runtime.recovery.RecoveryStrategyManager(0, 0)
        ) {
            @Override
            public StepFailureRecoveryResult recover(com.example.agent.runtime.step.contract.StepExecutionRequest executionRequest,
                                                     int attempt,
                                                     int decomposeAttempts,
                                                     Throwable error) {
                return StepFailureRecoveryResult.stop(
                        com.example.agent.runtime.recovery.RecoveryStrategy.STOP,
                        new ReflectionDownError("recovered_stop"),
                        null
                );
            }
        };

        fixture.coordinator = new StepExecutionCoordinator(
                fixture.stepRuntimeService,
                fixture.stepOutputSummaryBuilder,
                fixture.runtimeExecutionGate,
                fixture.runtimeApprovalGate,
                fixture.stepExecutionDelegate,
                fixture.hookManager,
                customRecovery,
                fixture.retryPolicy,
                fixture.reflectionService,
                fixture.runtimeEventDispatchService,
                fixture.runtimeContextUpdateService
        );

        assertThrows(RuntimeException.class, () -> fixture.coordinator.executeStep(
                new StepSpec("TOOL", Map.of("tool", "search")),
                new TaskRequest(),
                new TenantContext("t-1", "u-1", List.of(), "req-1", "trace-1"),
                "wf-1",
                "task-1",
                new AtomicLong(0),
                new RuntimeContext(),
                0,
                new ArrayList<>()
        ));

        verify(fixture.stepRuntimeService).failStep(any(), eq("REFLECTION_DOWN"), any(), any());
    }

    private TestCoordinatorFixture createBaseFixture() {
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

        StepFailureRecoveryService failureRecoveryService = new StepFailureRecoveryService(stepExecutionDelegate, 0, 0);
        RetryPolicy retryPolicy = new RetryPolicy(1, 1, 0.0);
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

        return new TestCoordinatorFixture(
                coordinator,
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
                contextUpdateService,
                eventPublisher
        );
    }

    private static class TestCoordinatorFixture {

        private StepExecutionCoordinator coordinator;
        private final StepRuntimeService stepRuntimeService;
        private final StepOutputSummaryBuilder stepOutputSummaryBuilder;
        private final RuntimeExecutionGate runtimeExecutionGate;
        private final RuntimeApprovalGate runtimeApprovalGate;
        private final StepExecutionDelegate stepExecutionDelegate;
        private final HookManager hookManager;
        private final StepFailureRecoveryService stepFailureRecoveryService;
        private final RetryPolicy retryPolicy;
        private final ReflectionService reflectionService;
        private final RuntimeEventDispatchService runtimeEventDispatchService;
        private final RuntimeContextUpdateService runtimeContextUpdateService;
        private final TestEventPublisher eventPublisher;

        private TestCoordinatorFixture(StepExecutionCoordinator coordinator,
                                       StepRuntimeService stepRuntimeService,
                                       StepOutputSummaryBuilder stepOutputSummaryBuilder,
                                       RuntimeExecutionGate runtimeExecutionGate,
                                       RuntimeApprovalGate runtimeApprovalGate,
                                       StepExecutionDelegate stepExecutionDelegate,
                                       HookManager hookManager,
                                       StepFailureRecoveryService stepFailureRecoveryService,
                                       RetryPolicy retryPolicy,
                                       ReflectionService reflectionService,
                                       RuntimeEventDispatchService runtimeEventDispatchService,
                                       RuntimeContextUpdateService runtimeContextUpdateService,
                                       TestEventPublisher eventPublisher) {
            this.coordinator = coordinator;
            this.stepRuntimeService = stepRuntimeService;
            this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
            this.runtimeExecutionGate = runtimeExecutionGate;
            this.runtimeApprovalGate = runtimeApprovalGate;
            this.stepExecutionDelegate = stepExecutionDelegate;
            this.hookManager = hookManager;
            this.stepFailureRecoveryService = stepFailureRecoveryService;
            this.retryPolicy = retryPolicy;
            this.reflectionService = reflectionService;
            this.runtimeEventDispatchService = runtimeEventDispatchService;
            this.runtimeContextUpdateService = runtimeContextUpdateService;
            this.eventPublisher = eventPublisher;
        }
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

    private static class ReflectionDownError extends RuntimeException implements ErrorCodeProvider {

        private ReflectionDownError(String message) {
            super(message);
        }

        @Override
        public String getErrorCode() {
            return "REFLECTION_DOWN";
        }
    }
}

