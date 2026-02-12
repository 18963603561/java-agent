package com.example.agent.streaming;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.evidence.EvidenceItem;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidenceStats;
import com.example.agent.capabilities.context.evidence.EvidenceType;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.streaming.payload.ContextBudgetSummary;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.streaming.sse.EventStreamService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEventPublisherTest {

    @Test
    void publishSnapshotAddsStructuredSummaryStats() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("结构化摘要");
        workingMemory.setKeyFacts(List.of("要点1", "要点2"));
        workingMemory.setUsedStructuredSummary(true);
        workingMemory.setSummaryVersion("v1");
        workingMemory.setSummaryChars(workingMemory.getSummary().length());
        workingMemory.setWorkingMemoryItems(workingMemory.getKeyFacts().size());

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-1");
        snapshot.setWorkingMemory(workingMemory);

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");
        publisher.publishSnapshot(tenantContext, "wf-1", new AtomicLong(0), snapshot, null, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_CREATED);
        assertNotNull(event);
        Map<String, Object> payload = event.getPayload();
        assertEquals(Boolean.TRUE, payload.get("usedStructuredSummary"));
        assertEquals("v1", payload.get("summaryVersion"));
        assertEquals(workingMemory.getSummary().length(), payload.get("summaryChars"));
        assertEquals(workingMemory.getKeyFacts().size(), payload.get("workingMemoryItems"));
    }

    @Test
    void publishSnapshotAddsLegacySummaryStats() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("旧摘要");
        workingMemory.setKeyFacts(List.of("事实1"));
        workingMemory.setUsedStructuredSummary(false);

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-2");
        snapshot.setWorkingMemory(workingMemory);

        TenantContext tenantContext = new TenantContext("t2", "u2", List.of(), "req", "trace");
        publisher.publishSnapshot(tenantContext, "wf-2", new AtomicLong(0), snapshot, null, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_CREATED);
        assertNotNull(event);
        Map<String, Object> payload = event.getPayload();
        assertEquals(Boolean.FALSE, payload.get("usedStructuredSummary"));
        assertEquals(null, payload.get("summaryVersion"));
        assertEquals(workingMemory.getSummary().length(), payload.get("summaryChars"));
        assertEquals(workingMemory.getKeyFacts().size(), payload.get("workingMemoryItems"));
    }

    @Test
    void publishSnapshotAddsEvidenceStatsWhenPresent() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        EvidenceItem toolCallEvidence = new EvidenceItem();
        toolCallEvidence.setEvidenceId("ev-tool-1");
        toolCallEvidence.setType(EvidenceType.TOOL_RESULT);
        toolCallEvidence.setSource("tool-a");
        toolCallEvidence.setRef("raw-1");
        toolCallEvidence.setDigest("result");

        EvidenceItem memory1 = new EvidenceItem();
        memory1.setEvidenceId("ev-memory-1");
        memory1.setType(EvidenceType.MEMORY);
        memory1.setSource("memory");
        memory1.setRef("mem-1");
        memory1.setDigest("v1");

        EvidenceItem memory2 = new EvidenceItem();
        memory2.setEvidenceId("ev-memory-2");
        memory2.setType(EvidenceType.MEMORY);
        memory2.setSource("memory");
        memory2.setRef("mem-2");

        EvidenceItem citation = new EvidenceItem();
        citation.setEvidenceId("ev-research-1");
        citation.setType(EvidenceType.RESEARCH);
        citation.setSource("web");
        citation.setRef("ref-1");
        citation.setDigest("label");

        EvidencePack pack = new EvidencePack();
        pack.setEvidences(List.of(toolCallEvidence, memory1, memory2, citation));

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setEvidencePack(pack);

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-e1");
        snapshot.setWorkingMemory(workingMemory);

        TenantContext tenantContext = new TenantContext("t3", "u3", List.of(), "req", "trace");
        publisher.publishSnapshot(tenantContext, "wf-3", new AtomicLong(0), snapshot, null, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_CREATED);
        assertNotNull(event);
        Map<String, Object> payload = event.getPayload();
        EvidenceStats stats = pack.getStats();
        assertNotNull(stats);
        assertEquals(Boolean.TRUE, payload.get("evidencePackPresent"));
        assertEquals(pack.getVersion(), payload.get("evidencePackVersion"));
        assertEquals(stats.getToolCount(), payload.get("evidenceToolCount"));
        assertEquals(stats.getMemoryCount(), payload.get("evidenceMemoryCount"));
        assertEquals(stats.getResearchCount(), payload.get("evidenceResearchCount"));
        assertEquals(stats.getTruncationCount(), payload.get("evidenceTruncationCount"));
        assertEquals(stats.getApproxChars(), payload.get("evidenceApproxChars"));
    }

    @Test
    void publishSnapshotKeepsEvidenceStatsOptionalWhenMissing() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        WorkingMemory workingMemory = new WorkingMemory();
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-e2");
        snapshot.setWorkingMemory(workingMemory);

        TenantContext tenantContext = new TenantContext("t4", "u4", List.of(), "req", "trace");
        publisher.publishSnapshot(tenantContext, "wf-4", new AtomicLong(0), snapshot, null, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_CREATED);
        assertNotNull(event);
        Map<String, Object> payload = event.getPayload();
        assertEquals(Boolean.FALSE, payload.get("evidencePackPresent"));
        assertEquals(0, payload.get("evidenceToolCount"));
        assertEquals(0, payload.get("evidenceMemoryCount"));
        assertEquals(0, payload.get("evidenceResearchCount"));
        assertEquals(0, payload.get("evidenceTruncationCount"));
        assertEquals(0, payload.get("evidenceApproxChars"));
        assertNull(payload.get("evidencePackVersion"));
    }

    @Test
    void publishSnapshotShouldEmitDisabledBudgetSummaryWhenAllocationMissing() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-budget-null");
        snapshot.setWorkingMemory(new WorkingMemory());

        TenantContext tenantContext = new TenantContext("t5", "u5", List.of(), "req", "trace");
        publisher.publishSnapshot(tenantContext, "wf-5", new AtomicLong(0), snapshot, null, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_CREATED);
        assertNotNull(event);
        Object budgetSummaryObj = event.getPayload().get("budgetSummary");
        assertTrue(budgetSummaryObj instanceof ContextBudgetSummary);
        ContextBudgetSummary summary = (ContextBudgetSummary) budgetSummaryObj;
        assertEquals(ContextBudgetAllocationState.DISABLED_BY_DEPENDENCY, summary.getAllocationState());
        assertEquals("missing_allocation", summary.getAllocationReason());
    }

    @Test
    void publishSnapshotStageShouldEmitCompressionStageEventWhenStageIsCompression() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-c1");
        WorkingMemory memory = new WorkingMemory();
        snapshot.setWorkingMemory(memory);

        TenantContext tenantContext = new TenantContext("t6", "u6", List.of(), "req", "trace");
        publisher.publishSnapshotStage(tenantContext,
                "wf-c1",
                new AtomicLong(0),
                snapshot,
                "snap-c1",
                null,
                null,
                null,
                null,
                ContextSnapshotStage.CONTEXT_COMPRESSION_SKIPPED,
                100,
                100);

        StreamEvent stageEvent = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_STAGE);
        assertNotNull(stageEvent);
        assertEquals(ContextSnapshotStage.CONTEXT_COMPRESSION_SKIPPED.name(), stageEvent.getPayload().get("stage"));

        StreamEvent compressionEvent = eventPublisher.findFirst(EventType.CONTEXT_COMPRESSION_STAGE);
        assertNotNull(compressionEvent);
        assertEquals(ContextSnapshotStage.CONTEXT_COMPRESSION_SKIPPED.name(), compressionEvent.getPayload().get("stage"));
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        private final List<StreamEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            // 不处理 ApplicationEvent 分支
        }

        public StreamEvent findFirst(EventType type) {
            return events.stream()
                    .filter(event -> event.getType() == type)
                    .findFirst()
                    .orElse(null);
        }
    }
}
