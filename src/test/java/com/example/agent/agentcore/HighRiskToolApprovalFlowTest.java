package com.example.agent.agentcore;

import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.execution.ToolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighRiskToolApprovalFlowTest {

    @Test
    void highRiskToolShouldWaitForApprovalThenExecute() throws Exception {
        ApprovalService approvalService = buildApprovalService(5);
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, eventPublisher, approvalService);

        CountDownLatch invokedLatch = new CountDownLatch(1);
        when(toolExecutor.execute(any(), any(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    invokedLatch.countDown();
                    return Map.of("ok", true);
                });

        TaskRequest request = buildRequest();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, Object>> future = executor.submit(() -> gateway.execute(
                request, tenantContext, "workflow-a", "task-a", new AtomicLong(), "danger_tool"));

        String requestId = waitForPendingRequestId(approvalService);
        assertEquals(1, invokedLatch.getCount());

        approvalService.decide("tenant-a", "workflow-a", requestId, true, "ok");

        assertTrue(invokedLatch.await(1, TimeUnit.SECONDS));
        Map<String, Object> result = future.get(1, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, result.get("ok"));

        executor.shutdownNow();
    }

    @Test
    void highRiskToolRejectedShouldNotExecute() throws Exception {
        ApprovalService approvalService = buildApprovalService(3);
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, eventPublisher, approvalService);

        TaskRequest request = buildRequest();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, Object>> future = executor.submit(() -> gateway.execute(
                request, tenantContext, "workflow-a", "task-a", new AtomicLong(), "danger_tool"));

        String requestId = waitForPendingRequestId(approvalService);
        approvalService.decide("tenant-a", "workflow-a", requestId, false, "reject");

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof ErrorCodeException);
        ErrorCodeException error = (ErrorCodeException) ex.getCause();
        assertEquals("APPROVAL_REJECTED", error.getErrorCode());
        verify(toolExecutor, never()).execute(any(), any(), anyString(), anyString(), anyString());

        executor.shutdownNow();
    }

    @Test
    void highRiskToolTimeoutShouldNotExecute() throws Exception {
        ApprovalService approvalService = buildApprovalService(1);
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, eventPublisher, approvalService);

        TaskRequest request = buildRequest();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Map<String, Object>> future = executor.submit(() -> gateway.execute(
                request, tenantContext, "workflow-a", "task-a", new AtomicLong(), "danger_tool"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof ErrorCodeException);
        ErrorCodeException error = (ErrorCodeException) ex.getCause();
        assertEquals("APPROVAL_TIMEOUT", error.getErrorCode());
        verify(toolExecutor, never()).execute(any(), any(), anyString(), anyString(), anyString());

        executor.shutdownNow();
    }

    @Test
    void nonHighRiskToolShouldExecuteDirectly() {
        ApprovalService approvalService = buildApprovalService(3);
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, eventPublisher, approvalService);

        when(toolExecutor.execute(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("ok", true));

        TaskRequest request = buildRequest();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        Map<String, Object> result = gateway.execute(
                request, tenantContext, "workflow-a", "task-a", new AtomicLong(), "safe_tool");

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.get("ok"));
        verify(toolExecutor, times(1)).execute(any(), any(), anyString(), anyString(), anyString());

        @SuppressWarnings("unchecked")
        Map<String, Object> pending = (Map<String, Object>) ReflectionTestUtils.getField(approvalService,
                "pendingApprovals");
        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }

    private ApprovalService buildApprovalService(int timeoutSeconds) {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(true);
        properties.setHighRiskTools(List.of("danger_tool"));
        properties.setTimeoutSeconds(timeoutSeconds);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties, metricsPublisher);
    }

    private TaskRequest buildRequest() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        Map<String, Object> context = new HashMap<>();
        context.put("snapshotId", "snapshot-a");
        request.setContext(context);
        return request;
    }

    private String waitForPendingRequestId(ApprovalService approvalService) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pending = (Map<String, Object>) ReflectionTestUtils.getField(approvalService,
                    "pendingApprovals");
            if (pending != null && !pending.isEmpty()) {
                return pending.keySet().iterator().next();
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        throw new AssertionError("审批请求未创建");
    }
}
