package com.example.agent.tools.enforcement;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.capabilities.tools.execution.ToolExecutor;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private EnforcementGateway buildGateway() {
        ToolExecutor toolExecutor = Mockito.mock(ToolExecutor.class);
        Mockito.when(toolExecutor.buildArguments(Mockito.any())).thenReturn(Map.of());
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(false);
        ApprovalService approvalService = new ApprovalService(properties, new MetricsPublisher(new SimpleMeterRegistry()));
        return new EnforcementGateway(toolExecutor, publisher, approvalService);
    }
}

