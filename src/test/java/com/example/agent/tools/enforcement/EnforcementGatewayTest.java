package com.example.agent.tools.enforcement;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.execution.ToolExecutor;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.error.GovernanceRejectionException;
import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.governance.approval.domain.ApprovalArgsDigestBuilder;
import com.example.agent.governance.approval.domain.ApprovalDecisionAwaiter;
import com.example.agent.governance.approval.domain.PendingApprovalStore;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnforcementGatewayTest {

    @Test
    void executeRejectsNullRequest() {
        EnforcementGateway gateway = buildGateway();

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> gateway.execute(null,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-1", "task-1", new AtomicLong(), "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("request 不能为空", ex.getReason());
    }

    @Test
    void executeRejectsBlankTenantId() {
        EnforcementGateway gateway = buildGateway();

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> gateway.execute(new TaskRequest(),
                        new TenantContext(" ", "u1", List.of(), "req", "trace"),
                        "wf-1", "task-1", new AtomicLong(), "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("tenantContext.tenantId 不能为空", ex.getReason());
    }

    @Test
    void executeRejectsBlankToolName() {
        EnforcementGateway gateway = buildGateway();

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> gateway.execute(new TaskRequest(),
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-1", "task-1", new AtomicLong(), "   "));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("toolName 不能为空", ex.getReason());
    }

    @Test
    void executeRejectsNullSeqCounter() {
        EnforcementGateway gateway = buildGateway();

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> gateway.execute(new TaskRequest(),
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-1", "task-1", null, "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("seqCounter 不能为空", ex.getReason());
    }

    @Test
    void executeShouldPublishBackpressureWhenRateLimited() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        Map<String, Object> context = new HashMap<>();
        context.put("governanceType", "rate_limit");
        context.put("trigger", "threshold_reject");
        Mockito.when(toolExecutor.execute(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.any()))
                .thenThrow(new GovernanceRejectionException(HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMITED",
                        "请求过于频繁",
                        context));
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, publisher, buildApprovalServiceDisabled());

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        assertThrows(GovernanceRejectionException.class,
                () -> gateway.execute(request,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-1",
                        "task-1",
                        new AtomicLong(),
                        "demo_tool"));

        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        Mockito.verify(publisher, Mockito.atLeast(1)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.BACKPRESSURE_APPLIED));
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.TOOL_ERROR));
    }

    @Test
    void executeShouldPublishCircuitAndBackpressureWhenCircuitOpen() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        Map<String, Object> context = new HashMap<>();
        context.put("governanceType", "circuit_breaker");
        context.put("trigger", "open_state");
        Mockito.when(toolExecutor.execute(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.any()))
                .thenThrow(new GovernanceRejectionException(HttpStatus.SERVICE_UNAVAILABLE,
                        "CIRCUIT_OPEN",
                        "熔断开启",
                        context));
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        EnforcementGateway gateway = new EnforcementGateway(toolExecutor, publisher, buildApprovalServiceDisabled());

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        assertThrows(GovernanceRejectionException.class,
                () -> gateway.execute(request,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"),
                        "wf-1",
                        "task-1",
                        new AtomicLong(),
                        "demo_tool"));

        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        Mockito.verify(publisher, Mockito.atLeast(1)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.CIRCUIT_OPENED));
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.BACKPRESSURE_APPLIED));
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.TOOL_ERROR));
    }

    private EnforcementGateway buildGateway() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        ApprovalService approvalService = buildApprovalServiceDisabled();
        return new EnforcementGateway(toolExecutor, publisher, approvalService);
    }

    private ApprovalService buildApprovalServiceDisabled() {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(false);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties,
                new PendingApprovalStore(metricsPublisher),
                new ApprovalDecisionAwaiter(),
                new ApprovalArgsDigestBuilder(),
                new GovernanceTelemetry(metricsPublisher));
    }
}
