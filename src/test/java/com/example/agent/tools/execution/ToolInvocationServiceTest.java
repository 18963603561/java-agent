package com.example.agent.tools.execution;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.execution.mapping.ToolFieldKeys;
import com.example.agent.capabilities.tools.execution.service.ToolInvocationService;
import com.example.agent.capabilities.tools.mcp.McpToolCallRequest;
import com.example.agent.capabilities.tools.mcp.McpToolCallResponse;
import com.example.agent.capabilities.tools.mcp.McpToolClient;
import com.example.agent.capabilities.tools.sandbox.SandboxExecutor;
import com.example.agent.capabilities.tools.sandbox.SandboxResult;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ToolInvocationServiceTest {

    @Test
    void invokeMergesMcpAndSandboxPayload() {
        ToolInvocationService service = new ToolInvocationService();
        SandboxExecutor sandboxExecutor = Mockito.mock(SandboxExecutor.class);
        McpToolClient mcpToolClient = Mockito.mock(McpToolClient.class);

        when(sandboxExecutor.execute(eq("demo_tool"), any(), any(), any()))
                .thenReturn(new SandboxResult("SKIPPED", Map.of("policy", "ok"), null));
        when(mcpToolClient.callTool(any(), any()))
                .thenReturn(new McpToolCallResponse("call-1", "SUCCESS", Map.of("value", "ok"), null));

        Map<String, Object> result = service.invoke(
                sandboxExecutor,
                mcpToolClient,
                new TaskRequest(),
                new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "demo_tool",
                new McpToolCallRequest(),
                Map.of("query", "ping"));

        assertEquals("ok", result.get("value"));
        assertEquals("ok", ((Map<?, ?>) result.get(ToolFieldKeys.SANDBOX)).get("policy"));
        assertEquals("SKIPPED", result.get(ToolFieldKeys.SANDBOX_STATUS));
    }
}

