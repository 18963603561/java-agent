package com.example.agent.runtime.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.common.error.ErrorCodeProvider;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import com.example.agent.runtime.step.RuntimeContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class StepFailureRecoveryServiceTest {

    @Test
    void recoverReturnsRetryWhenRetryableAndAttemptBelowMaxRetries() {
        StepExecutionDelegate delegate = Mockito.mock(StepExecutionDelegate.class);
        StepFailureRecoveryService service = new StepFailureRecoveryService(delegate, 3, 0);

        StepExecutionRequest request = buildExecutionRequest(null);
        Throwable error = new TestError("RATE_LIMITED", "rate limited");

        StepFailureRecoveryService.StepFailureRecoveryResult result = service.recover(request, 1, 0, error);

        assertEquals(StepFailureRecoveryService.StepFailureRecoveryResult.Action.RETRY, result.getAction());
        assertEquals("RATE_LIMITED", assertInstanceOf(ErrorCodeProvider.class, error).getErrorCode());
        assertEquals(error, result.getError());
    }

    @Test
    void recoverReturnsReplanWhenDecomposeAndUnderMaxDecompose() {
        StepExecutionDelegate delegate = Mockito.mock(StepExecutionDelegate.class);
        StepFailureRecoveryService service = new StepFailureRecoveryService(delegate, 0, 2);

        StepExecutionRequest request = buildExecutionRequest(null);
        Throwable error = new TestError("BUDGET_EXCEEDED", "budget exceeded");

        StepFailureRecoveryService.StepFailureRecoveryResult result = service.recover(request, 1, 0, error);

        assertEquals(StepFailureRecoveryService.StepFailureRecoveryResult.Action.REPLAN, result.getAction());
        assertEquals(error, result.getError());
    }

    @Test
    void recoverReturnsStopWhenNonRetryable() {
        StepExecutionDelegate delegate = Mockito.mock(StepExecutionDelegate.class);
        StepFailureRecoveryService service = new StepFailureRecoveryService(delegate, 3, 3);

        StepExecutionRequest request = buildExecutionRequest(null);
        Throwable error = new TestError("POLICY_DENIED", "denied");

        StepFailureRecoveryService.StepFailureRecoveryResult result = service.recover(request, 1, 0, error);

        assertEquals(StepFailureRecoveryService.StepFailureRecoveryResult.Action.STOP, result.getAction());
        assertEquals(error, result.getError());
    }

    @Test
    void recoverExecutesFallbackToolWhenRetryableButNoRetriesLeft() {
        StepExecutionDelegate delegate = Mockito.mock(StepExecutionDelegate.class);
        StepFailureRecoveryService service = new StepFailureRecoveryService(delegate, 0, 0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("ok", true);
        when(delegate.executeTool(any(), eq("fallback-tool"), eq(null)))
                .thenReturn(StepExecutionOutput.fromPayload(payload));
        when(delegate.resolveToolName(any(), any())).thenReturn("origin-tool");

        StepExecutionRequest request = buildExecutionRequest("fallback-tool");
        Throwable error = new TestError("RATE_LIMITED", "rate limited");

        StepFailureRecoveryService.StepFailureRecoveryResult result = service.recover(request, 1, 0, error);

        assertEquals(StepFailureRecoveryService.StepFailureRecoveryResult.Action.FALLBACK_SUCCESS, result.getAction());
        assertEquals("fallback-tool", result.getFallbackTool());
        assertNull(result.getError());
        assertNotNull(result.getFallbackOutput());
        assertEquals("origin-tool", result.getFallbackOutput().getFallbackFrom());
        assertEquals("rate limited", result.getFallbackOutput().getFallbackReason());
        assertEquals(Boolean.TRUE, result.getFallbackOutput().getPayload().get("ok"));
    }

    @Test
    void recoverStopsWhenFallbackOutputEmpty() {
        StepExecutionDelegate delegate = Mockito.mock(StepExecutionDelegate.class);
        StepFailureRecoveryService service = new StepFailureRecoveryService(delegate, 0, 0);

        when(delegate.executeTool(any(), eq("fallback-tool"), eq(null)))
                .thenReturn(StepExecutionOutput.fromPayload(Map.of()));

        StepExecutionRequest request = buildExecutionRequest("fallback-tool");
        Throwable error = new TestError("RATE_LIMITED", "rate limited");

        StepFailureRecoveryService.StepFailureRecoveryResult result = service.recover(request, 1, 0, error);

        assertEquals(StepFailureRecoveryService.StepFailureRecoveryResult.Action.STOP, result.getAction());
        IllegalStateException ex = assertInstanceOf(IllegalStateException.class, result.getError());
        assertEquals("fallback_output_empty", ex.getMessage());
        assertEquals("fallback-tool", result.getFallbackTool());
    }

    private StepExecutionRequest buildExecutionRequest(String fallbackTool) {
        StepSpec step = new StepSpec();
        step.setStepType("TOOL");
        if (fallbackTool != null) {
            step.setArguments(Map.of("fallbackTool", fallbackTool));
        }
        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        RuntimeContext runtimeContext = new RuntimeContext(new HashMap<>());
        StepInputView stepInputView = step.toInputView(runtimeContext);
        return new StepExecutionRequest(
                step,
                request,
                Map.of(),
                stepInputView,
                runtimeContext,
                null,
                "wf-1",
                "task-1",
                new AtomicLong(0),
                null,
                null
        );
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
