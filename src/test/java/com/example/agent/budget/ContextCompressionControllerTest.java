package com.example.agent.budget;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.LongTermMemory;
import com.example.agent.capabilities.context.MemoryRef;
import com.example.agent.capabilities.context.WorkingMemory;
import com.example.agent.capabilities.memory.model.ConversationSummary;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.MemoryStore;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.memory.model.WorkingMemorySummary;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextTrimReport;
import com.example.agent.budget.trim.ContextCompressionController;
import com.example.agent.budget.trim.ContextCompressionProperties;
import com.example.agent.budget.trim.ContextCompressionRequest;
import com.example.agent.budget.trim.ContextCompressionResult;
import com.example.agent.budget.trim.ContextSection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextCompressionControllerTest {

    @Test
    void compressTriggeredWhenOverBudget() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setMinIntervalSeconds(0);

        ContextCompressionController controller = new ContextCompressionController(
                memoryStore,
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()),
                properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        MemoryRecord compressed = buildCompressedRecord();
        when(memoryStore.compress(any(), eq(tenantContext))).thenReturn(compressed);

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-1",
                "s1");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertTrue(result.isTriggered());
        assertNotNull(result.getAfterCompressTokens());
        assertTrue(result.getAfterCompressTokens() < result.getAfterTrimTokens());
        verify(memoryStore, times(1)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressSkippedDuringCooldown() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setMinIntervalSeconds(60);

        ContextCompressionController controller = new ContextCompressionController(
                memoryStore,
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()),
                properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        MemoryRecord compressed = buildCompressedRecord();
        when(memoryStore.compress(any(), eq(tenantContext))).thenReturn(compressed);

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-2",
                "s2");

        ContextCompressionResult first = controller.compressIfNeeded(request);
        ContextCompressionResult second = controller.compressIfNeeded(request);

        assertTrue(first.isTriggered());
        assertTrue(second.isSkippedCooldown());
        verify(memoryStore, times(1)).compress(any(), eq(tenantContext));
    }

    @Test
    void compressDisabledNeverTriggers() {
        MemoryStore memoryStore = Mockito.mock(MemoryStore.class);
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.setEnabled(false);

        ContextCompressionController controller = new ContextCompressionController(
                memoryStore,
                new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()),
                properties);

        ContextBudgetAllocation allocation = buildAllocation(50, 20);
        ContextTrimReport trimReport = buildTrimReport(300, 200, 150);
        ContextSnapshot snapshot = buildSnapshot("a".repeat(400), "b".repeat(200));
        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextCompressionRequest request = new ContextCompressionRequest(
                snapshot,
                allocation,
                trimReport,
                tenantContext,
                "wf-3",
                "s3");

        ContextCompressionResult result = controller.compressIfNeeded(request);

        assertTrue(!result.isTriggered());
        verify(memoryStore, times(0)).compress(any(), eq(tenantContext));
    }

    private ContextBudgetAllocation buildAllocation(int totalTokens, int workingMemoryBudget) {
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(totalTokens);
        EnumMap<ContextSection, Integer> budgets = new EnumMap<>(ContextSection.class);
        budgets.put(ContextSection.WORKING_MEMORY, workingMemoryBudget);
        allocation.setSectionTokens(budgets);
        return allocation;
    }

    private ContextTrimReport buildTrimReport(int beforeTokens, int afterTokens, int workingMemoryTokens) {
        ContextTrimReport report = new ContextTrimReport();
        report.setTotalBeforeTokens(beforeTokens);
        report.setTotalAfterTokens(afterTokens);
        EnumMap<ContextSection, Integer> sections = new EnumMap<>(ContextSection.class);
        sections.put(ContextSection.WORKING_MEMORY, workingMemoryTokens);
        report.setSectionTokensAfter(sections);
        return report;
    }

    private ContextSnapshot buildSnapshot(String summary, String snippet) {
        WorkingMemory memory = new WorkingMemory();
        memory.setSummary(summary);
        LongTermMemory longTermMemory = new LongTermMemory();
        MemoryRef ref = new MemoryRef();
        ref.setMemoryId("m1");
        ref.setSnippet(snippet);
        longTermMemory.setMemoryRefs(List.of(ref));
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);
        snapshot.setLongTermMemory(longTermMemory);
        return snapshot;
    }

    private MemoryRecord buildCompressedRecord() {
        MemoryRecord record = new MemoryRecord();
        record.setMemoryId("cm1");
        record.setSummary("压缩摘要");

        ConversationSummary conversationSummary = new ConversationSummary();
        conversationSummary.setVersion("v1");
        conversationSummary.setSummary("压缩摘要");
        conversationSummary.setBullets(List.of("要点1"));
        record.setConversationSummary(conversationSummary);

        WorkingMemorySummary workingMemorySummary = new WorkingMemorySummary();
        workingMemorySummary.setVersion("v1");
        workingMemorySummary.setSummary("压缩工作记忆摘要");
        workingMemorySummary.setItems(List.of("事项1"));
        record.setWorkingMemorySummary(workingMemorySummary);
        return record;
    }
}

