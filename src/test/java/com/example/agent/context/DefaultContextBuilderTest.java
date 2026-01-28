package com.example.agent.context;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextBudgetAllocator;
import com.example.agent.budget.ContextBudgetProperties;
import com.example.agent.budget.ContextBudgetRequest;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.budget.ContextPruner;
import com.example.agent.common.TaskRequest;
import com.example.agent.memory.ConversationSummary;
import com.example.agent.memory.MemoryRecallResult;
import com.example.agent.memory.MemoryRecord;
import com.example.agent.memory.WorkingMemorySummary;
import com.example.agent.runtime.ReactObservation;
import com.example.agent.tools.ToolCatalog;
import com.example.agent.tools.ToolQuery;
import com.example.agent.tools.ToolSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DefaultContextBuilderTest {

    @Test
    void buildCreatesSnapshotWithMemoryAndToolSummary() {
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        ContextBudgetAllocator budgetAllocator = Mockito.mock(ContextBudgetAllocator.class);
        ContextPruner contextPruner = Mockito.mock(ContextPruner.class);
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(2048);

        DefaultContextBuilder builder = new DefaultContextBuilder(toolCatalog, budgetAllocator, contextPruner,
                budgetProperties);
        ReflectionTestUtils.setField(builder, "defaultTokenBudget", 2048);
        ReflectionTestUtils.setField(builder, "maxWorkingSummaryChars", 200);

        ToolSummary summary = new ToolSummary();
        summary.setToolName("tool-a");
        summary.setDescription("demo");
        when(toolCatalog.listSummaries(any(ToolQuery.class))).thenReturn(List.of(summary));

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(2048);
        when(budgetAllocator.allocate(any(ContextBudgetRequest.class))).thenReturn(allocation);

        ContextPruneResult pruneResult = new ContextPruneResult();
        when(contextPruner.prune(any())).thenReturn(pruneResult);

        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("m1");
        record.setSummary("记忆摘要");
        record.setLayer("recent");
        MemoryRecallResult recallResult = MemoryRecallResult.hit(List.of(record), "召回摘要");

        TaskRequest request = new TaskRequest();
        request.setQuery("测试问题");
        request.setSessionId("s1");

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId("wf-1");
        buildRequest.setTaskId("task-1");
        buildRequest.setRecallResult(recallResult);
        buildRequest.setObservations(List.of(new ReactObservation("obs", "tool", Instant.now())));
        buildRequest.setRuntimeContext(Map.of("successCriteria", "ok"));

        ContextBuildResult result = builder.build(buildRequest);

        assertNotNull(result);
        assertNotNull(result.getSnapshot());
        assertEquals("t1", result.getSnapshot().getRuntimeMeta().getTenantId());
        assertEquals("召回摘要", result.getSnapshot().getWorkingMemory().getSummary());
        assertTrue(result.getSnapshot().getToolState().getAvailableTools().size() > 0);
    }

    @Test
    void buildUsesStructuredSummaryWhenAvailable() {
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(2048);
        DefaultContextBuilder builder = new DefaultContextBuilder(null, null, null, budgetProperties);
        ReflectionTestUtils.setField(builder, "defaultTokenBudget", 2048);
        ReflectionTestUtils.setField(builder, "maxWorkingSummaryChars", 200);

        ConversationSummary conversationSummary = new ConversationSummary();
        conversationSummary.setVersion("v1");
        conversationSummary.setSummary("结构化会话摘要");

        WorkingMemorySummary workingMemorySummary = new WorkingMemorySummary();
        workingMemorySummary.setVersion("v1");
        workingMemorySummary.setSummary("结构化工作记忆摘要");
        workingMemorySummary.setItems(List.of("要点1", "要点2"));

        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("m2");
        record.setLayer("compressed");
        record.setConversationSummary(conversationSummary);
        record.setWorkingMemorySummary(workingMemorySummary);
        record.setSummary("旧摘要");
        MemoryRecallResult recallResult = MemoryRecallResult.hit(List.of(record), "召回摘要");

        TaskRequest request = new TaskRequest();
        request.setQuery("测试问题");
        request.setSessionId("s2");

        TenantContext tenantContext = new TenantContext("t2", "u2", List.of(), "req", "trace");

        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId("wf-2");
        buildRequest.setTaskId("task-2");
        buildRequest.setRecallResult(recallResult);

        ContextBuildResult result = builder.build(buildRequest);

        assertNotNull(result);
        assertNotNull(result.getSnapshot());
        assertEquals("结构化会话摘要", result.getSnapshot().getWorkingMemory().getSummary());
        assertEquals(List.of("要点1", "要点2"), result.getSnapshot().getWorkingMemory().getKeyFacts());
        assertEquals(Boolean.TRUE, result.getSnapshot().getWorkingMemory().getUsedStructuredSummary());
    }
}
