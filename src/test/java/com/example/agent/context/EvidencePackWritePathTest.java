package com.example.agent.context;

import com.example.agent.agentcore.SandboxExecutor;
import com.example.agent.agentcore.ToolCache;
import com.example.agent.agentcore.ToolExecutor;
import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.auth.TenantContext;
import com.example.agent.budget.TokenBudgetManager;
import com.example.agent.budget.TokenUsageRecord;
import com.example.agent.common.TaskRequest;
import com.example.agent.memory.MemoryRecallProperties;
import com.example.agent.memory.MemoryRecallResult;
import com.example.agent.memory.MemoryRecallService;
import com.example.agent.memory.MemoryRecord;
import com.example.agent.memory.MemorySearchResult;
import com.example.agent.memory.MemoryStore;
import com.example.agent.memory.WorkingMemorySummary;
import com.example.agent.model.ModelDefinition;
import com.example.agent.model.ModelRouter;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.sandbox.SandboxResult;
import com.example.agent.tools.McpToolCallResponse;
import com.example.agent.tools.McpToolClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EvidencePackWritePathTest {

    @Test
    void toolExecutionAppendsEvidence() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        McpToolClient mcpToolClient = Mockito.mock(McpToolClient.class);
        SandboxExecutor sandboxExecutor = Mockito.mock(SandboxExecutor.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        EvidencePackService evidencePackService = new EvidencePackService(metricsPublisher);
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
        when(mcpToolClient.callTool(any(), any()))
                .thenReturn(new McpToolCallResponse("call-1", "SUCCESS", Map.of("value", "ok"), null));

        ToolExecutor executor = new ToolExecutor(toolRegistry, mcpToolClient, toolCache, sandboxExecutor,
                tokenBudgetManager, modelRouter, objectMapper, metricsPublisher, tracingPublisher, evidencePackService);
        ReflectionTestUtils.setField(executor, "cacheEnabled", false);

        Map<String, Object> context = new HashMap<>();
        context.put("workflowId", "workflow-1");
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setContext(context);

        executor.execute(request, new TenantContext("t1", "u1", List.of(), "req", "trace"),
                "usage-1", "demo_tool", "task-1");

        EvidencePack pack = (EvidencePack) context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
        assertNotNull(pack);
        assertNotNull(pack.getToolCalls());
        assertEquals(1, pack.getToolCalls().size());
        ToolCallEvidence evidence = pack.getToolCalls().get(0);
        assertEquals("demo_tool", evidence.getToolName());
        assertEquals("SUCCESS", evidence.getStatus());
        assertNotNull(evidence.getDurationMs());
        assertTrue(evidence.getDurationMs() >= 0);
        assertNotNull(pack.getStats());
        assertEquals(1, pack.getStats().getToolCallsCount());
    }

    @Test
    void recallAppendsMemoryEvidence() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setEnabled(true);
        properties.setMinQueryLength(1);
        properties.setLimit(5);
        properties.setMaxRecordChars(200);
        properties.setMaxSummaryChars(200);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        EvidencePackService evidencePackService = new EvidencePackService(metricsPublisher);
        MemoryRecallService service = new MemoryRecallService(memoryStore, properties, evidencePackService);

        MemoryRecord recordA = new MemoryRecord();
        recordA.setMemoryId("memory-a");
        recordA.setSessionId("session-1");
        recordA.setLayer("recent");
        recordA.setExpiresAt(Instant.parse("2026-01-28T00:00:00Z"));
        WorkingMemorySummary workingSummary = new WorkingMemorySummary();
        workingSummary.setVersion("v1");
        recordA.setWorkingMemorySummary(workingSummary);

        MemoryRecord recordB = new MemoryRecord();
        recordB.setMemoryId("memory-b");
        recordB.setSessionId("session-1");
        recordB.setLayer("recent");

        MemorySearchResult searchResult = new MemorySearchResult(List.of(recordA, recordB));
        when(memoryStore.search(any(), any())).thenReturn(searchResult);

        Map<String, Object> context = new HashMap<>();
        context.put("workflowId", "workflow-1");
        TaskRequest request = new TaskRequest();
        request.setQuery("hello");
        request.setSessionId("session-1");
        request.setContext(context);

        MemoryRecallResult result = service.recall(request, context,
                new TenantContext("t1", "u1", List.of(), "req", "trace"));
        assertTrue(result.isUsed());

        EvidencePack pack = (EvidencePack) context.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
        assertNotNull(pack);
        assertNotNull(pack.getMemoriesUsed());
        assertEquals(2, pack.getMemoriesUsed().size());
        MemoryEvidence evidence = pack.getMemoriesUsed().get(0);
        assertEquals("memory-a", evidence.getMemoryId());
        assertNotNull(evidence.getExpiresAt());
        assertEquals("v1", evidence.getSummaryVersion());
        assertNotNull(pack.getStats());
        assertEquals(2, pack.getStats().getMemoriesCount());
    }
}
