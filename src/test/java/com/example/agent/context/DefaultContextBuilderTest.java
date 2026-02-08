package com.example.agent.context;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.token.ContextBudgetAllocator;
import com.example.agent.budget.token.ContextBudgetProperties;
import com.example.agent.budget.token.ContextBudgetRequest;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.budget.trim.ContextPruner;
import com.example.agent.budget.trim.ContextSection;
import com.example.agent.budget.trim.ContextTrimReport;
import com.example.agent.budget.trim.ContextTrimRequest;
import com.example.agent.budget.trim.ContextTrimResult;
import com.example.agent.budget.trim.ContextTrimmer;
import com.example.agent.budget.trim.DefaultContextTrimmer;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.ConversationSummary;
import com.example.agent.capabilities.memory.MemoryRecallResult;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.memory.WorkingMemorySummary;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.react.ReactObservation;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import com.example.agent.capabilities.tools.ToolCatalogService;
import com.example.agent.capabilities.tools.ToolQuery;
import com.example.agent.capabilities.tools.ToolSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.ContextBuildResult;
import com.example.agent.capabilities.context.DefaultContextBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DefaultContextBuilderTest {

    @Test
    void buildCreatesSnapshotWithMemoryAndToolSummary() {
        ToolCatalogService toolCatalog = Mockito.mock(ToolCatalogService.class);
        ContextBudgetAllocator budgetAllocator = Mockito.mock(ContextBudgetAllocator.class);
        ContextPruner contextPruner = Mockito.mock(ContextPruner.class);
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(2048);

        DefaultContextBuilder builder = new DefaultContextBuilder(toolCatalog, budgetAllocator, contextPruner,
                null, null, budgetProperties, null, new MetricsPublisher(new SimpleMeterRegistry()));
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
        DefaultContextBuilder builder = new DefaultContextBuilder(null, null, null, null, null, budgetProperties,
                null, new MetricsPublisher(new SimpleMeterRegistry()));
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
    @Test
    void buildGeneratesTrimReportWhenBudgetEnabled() {
        ContextBudgetAllocator budgetAllocator = Mockito.mock(ContextBudgetAllocator.class);
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(200);

        TokenEstimator tokenEstimator = new TokenEstimator();
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        DefaultContextTrimmer contextTrimmer = new DefaultContextTrimmer(tokenEstimator, metricsPublisher, budgetProperties);

        DefaultContextBuilder builder = new DefaultContextBuilder(null, budgetAllocator, null, contextTrimmer,
                null, budgetProperties, null, new MetricsPublisher(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(builder, "defaultTokenBudget", 200);
        ReflectionTestUtils.setField(builder, "maxWorkingSummaryChars", 1000);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(80);
        EnumMap<ContextSection, Integer> sectionTokens = new EnumMap<>(ContextSection.class);
        for (ContextSection section : ContextSection.values()) {
            sectionTokens.put(section, 0);
        }
        sectionTokens.put(ContextSection.WORKING_MEMORY, 20);
        sectionTokens.put(ContextSection.USER_INPUT, 10);
        allocation.setSectionTokens(sectionTokens);
        when(budgetAllocator.allocate(any(ContextBudgetRequest.class))).thenReturn(allocation);

        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("m3");
        record.setSummary("a".repeat(600));
        record.setLayer("recent");
        MemoryRecallResult recallResult = MemoryRecallResult.hit(List.of(record), "a".repeat(600));

        TaskRequest request = new TaskRequest();
        request.setQuery("a".repeat(200));
        request.setSessionId("s3");

        TenantContext tenantContext = new TenantContext("t3", "u3", List.of(), "req", "trace");

        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId("wf-3");
        buildRequest.setTaskId("task-3");
        buildRequest.setRecallResult(recallResult);

        ContextBuildResult result = builder.build(buildRequest);

        assertNotNull(result.getTrimReport());
        assertTrue(result.getTrimReport().getTotalAfterTokens() <= allocation.getTotalTokens());
    }

    @Test
    void buildPublishesContextTrimmedStageEvent() {
        ContextBudgetAllocator budgetAllocator = Mockito.mock(ContextBudgetAllocator.class);
        ContextTrimmer contextTrimmer = Mockito.mock(ContextTrimmer.class);
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(200);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        when(eventStreamService.nextSequence(any(), any())).thenReturn(1L);
        ContextEventPublisher contextEventPublisher = new ContextEventPublisher(
                eventPublisher,
                eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        DefaultContextBuilder builder = new DefaultContextBuilder(null, budgetAllocator, null, contextTrimmer,
                null, budgetProperties, contextEventPublisher, new MetricsPublisher(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(builder, "defaultTokenBudget", 200);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(80);
        when(budgetAllocator.allocate(any(ContextBudgetRequest.class))).thenReturn(allocation);

        ContextTrimReport report = new ContextTrimReport();
        report.setTotalBeforeTokens(120);
        report.setTotalAfterTokens(80);
        report.setReasons(List.of("OVER_BUDGET"));
        ContextTrimResult trimResult = new ContextTrimResult();
        trimResult.setReport(report);
        when(contextTrimmer.trim(any(ContextTrimRequest.class))).thenReturn(trimResult);

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        request.setSessionId("s1");

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId("wf-1");

        builder.build(buildRequest);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_STAGE);
        assertNotNull(event);
        assertEquals(ContextSnapshotStage.CONTEXT_TRIMMED.name(), event.getPayload().get("stage"));
        assertNotNull(event.getPayload().get("trimSummary"));
    }

    static class TestEventPublisher implements org.springframework.context.ApplicationEventPublisher {
        private final List<StreamEvent> events = new java.util.ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(org.springframework.context.ApplicationEvent event) {
            // 忽略 ApplicationEvent 分支
        }

        public StreamEvent findFirst(EventType type) {
            return events.stream()
                    .filter(event -> event.getType() == type)
                    .findFirst()
                    .orElse(null);
        }
    }
}
