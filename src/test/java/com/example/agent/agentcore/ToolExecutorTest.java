package com.example.agent.agentcore;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.TokenBudgetManager;
import com.example.agent.budget.TokenUsageRecord;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelDefinition;
import com.example.agent.model.ModelRouter;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.sandbox.SandboxResult;
import com.example.agent.tools.McpToolCallRequest;
import com.example.agent.tools.McpToolCallResponse;
import com.example.agent.tools.McpToolClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutorTest {

    @Test
    void usesCacheWhenAvailable() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        McpToolClient mcpToolClient = Mockito.mock(McpToolClient.class);
        SandboxExecutor sandboxExecutor = Mockito.mock(SandboxExecutor.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        ObjectProvider<StringRedisTemplate> redisProvider = Mockito.mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCache toolCache = new ToolCache(redisProvider, objectMapper);

        ModelDefinition definition = new ModelDefinition();
        definition.setModelId("mock");
        definition.setProvider("mock");
        when(modelRouter.route(any())).thenReturn(definition);

        TokenUsageRecord usageRecord = new TokenUsageRecord();
        usageRecord.setTotalTokens(0);
        when(tokenBudgetManager.recordUsage(any(), any())).thenReturn(usageRecord);
        when(toolRegistry.resolve("demo_tool")).thenReturn("demo_tool");

        ToolExecutor executor = new ToolExecutor(toolRegistry, mcpToolClient, toolCache, sandboxExecutor,
                tokenBudgetManager, modelRouter, objectMapper, metricsPublisher, tracingPublisher);
        ReflectionTestUtils.setField(executor, "cacheEnabled", true);
        ReflectionTestUtils.setField(executor, "cacheTtlSeconds", 300L);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(Map.of("tool", "demo_tool"));

        String cacheKey = executor.buildCacheKey("demo_tool", executor.buildArguments(request));
        toolCache.put(cacheKey, Map.of("value", "cached"));

        Map<String, Object> result = executor.execute(request,
                new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "usage-1", "demo_tool", "task-1");

        assertEquals(true, result.get("cacheHit"));
        verify(mcpToolClient, never()).callTool(any(McpToolCallRequest.class), any());
    }

    @Test
    void retriesOnRetryableError() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        McpToolClient mcpToolClient = Mockito.mock(McpToolClient.class);
        SandboxExecutor sandboxExecutor = Mockito.mock(SandboxExecutor.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        ObjectProvider<StringRedisTemplate> redisProvider = Mockito.mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCache toolCache = new ToolCache(redisProvider, objectMapper);

        ModelDefinition definition = new ModelDefinition();
        definition.setModelId("mock");
        definition.setProvider("mock");
        when(modelRouter.route(any())).thenReturn(definition);

        TokenUsageRecord usageRecord = new TokenUsageRecord();
        usageRecord.setTotalTokens(1);
        when(tokenBudgetManager.recordUsage(any(), any())).thenReturn(usageRecord);
        when(toolRegistry.resolve("demo_tool")).thenReturn("demo_tool");
        when(sandboxExecutor.execute(eq("demo_tool"), any(), any(), any()))
                .thenReturn(new SandboxResult("SKIPPED", Map.of(), null));

        McpToolCallResponse okResponse = new McpToolCallResponse("call-1", "SUCCESS", Map.of("value", "ok"), null);
        when(mcpToolClient.callTool(any(McpToolCallRequest.class), any()))
                .thenThrow(new ErrorCodeException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "MCP_UNAVAILABLE", "down"))
                .thenReturn(okResponse);

        ToolExecutor executor = new ToolExecutor(toolRegistry, mcpToolClient, toolCache, sandboxExecutor,
                tokenBudgetManager, modelRouter, objectMapper, metricsPublisher, tracingPublisher);
        ReflectionTestUtils.setField(executor, "cacheEnabled", false);
        ReflectionTestUtils.setField(executor, "maxAttempts", 2);
        ReflectionTestUtils.setField(executor, "baseDelayMs", 0L);
        ReflectionTestUtils.setField(executor, "maxDelayMs", 0L);
        ReflectionTestUtils.setField(executor, "jitterRatio", 0.0);

        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        Map<String, Object> result = executor.execute(request,
                new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "usage-2", "demo_tool", "task-2");

        assertTrue(result.containsKey("result"));
        verify(mcpToolClient, times(2)).callTool(any(McpToolCallRequest.class), any());
        verify(metricsPublisher, times(1)).increment(eq("tool.call.count"), eq("trace"));
        verify(metricsPublisher, times(1)).recordTime(eq("tool.call.latency.ms"), anyLong(), eq("trace"));
    }
}
