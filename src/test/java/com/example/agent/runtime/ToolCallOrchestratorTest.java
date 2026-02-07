package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.llm.ToolCallOrchestrator;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCallOrchestratorTest {

    @Test
    void executeToolCallReturnsSuccessWhenGatewaySucceeds() {
        EnforcementGateway enforcementGateway = mock(EnforcementGateway.class);
        when(enforcementGateway.executeWithArguments(any(), any(), anyString(), anyString(), any(), anyString(), anyMap()))
                .thenReturn(Map.of("ok", true));

        ToolCallOrchestrator orchestrator = new ToolCallOrchestrator(enforcementGateway);
        ToolCallOrchestrator.ToolCallOutcome outcome = orchestrator.executeToolCall(
                new TaskRequest(),
                new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "wf",
                "task",
                new AtomicLong(0),
                "toolA",
                Map.of("k", "v")
        );

        assertEquals(ToolCallOrchestrator.TOOL_STATUS_SUCCESS, outcome.getStatus());
        assertNotNull(outcome.getResult());
        assertEquals(true, outcome.getResult().get("ok"));
    }

    @Test
    void executeToolCallReturnsFailureWithMappedCode() {
        EnforcementGateway enforcementGateway = mock(EnforcementGateway.class);
        when(enforcementGateway.executeWithArguments(any(), any(), anyString(), anyString(), any(), anyString(), anyMap()))
                .thenThrow(new ErrorCodeException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "limited"));

        ToolCallOrchestrator orchestrator = new ToolCallOrchestrator(enforcementGateway);
        ToolCallOrchestrator.ToolCallOutcome outcome = orchestrator.executeToolCall(
                new TaskRequest(),
                new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "wf",
                "task",
                new AtomicLong(0),
                "toolA",
                Map.of("k", "v")
        );

        assertEquals(ToolCallOrchestrator.TOOL_STATUS_FAILED, outcome.getStatus());
        assertEquals(ToolCallOrchestrator.TOOL_RATE_LIMITED, outcome.getErrorCode());
        assertTrue(outcome.isRetryable());
    }

    @Test
    void mapToolErrorCodeAndRetryableDecision() {
        ToolCallOrchestrator orchestrator = new ToolCallOrchestrator(mock(EnforcementGateway.class));

        String code = orchestrator.mapToolErrorCode(
                new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "down")
        );
        assertEquals(ToolCallOrchestrator.TOOL_UNAVAILABLE, code);
        assertTrue(orchestrator.isRetryableToolCode(code));
        assertFalse(orchestrator.isRetryableToolCode(ToolCallOrchestrator.TOOL_NOT_FOUND));
    }
}
