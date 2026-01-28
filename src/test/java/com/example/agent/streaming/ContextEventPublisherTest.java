package com.example.agent.streaming;

import com.example.agent.auth.TenantContext;
import com.example.agent.context.Citation;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.EvidenceStats;
import com.example.agent.context.MemoryEvidence;
import com.example.agent.context.ToolCallEvidence;
import com.example.agent.context.WorkingMemory;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        ToolCallEvidence toolCallEvidence = new ToolCallEvidence();
        toolCallEvidence.setToolName("tool-a");
        toolCallEvidence.setArgsDigest("args");
        toolCallEvidence.setResultDigest("result");
        toolCallEvidence.setStatus("SUCCESS");
        toolCallEvidence.setErrorCode("E1");
        toolCallEvidence.setToolCallId("call-1");

        MemoryEvidence memory1 = new MemoryEvidence();
        memory1.setMemoryId("mem-1");
        memory1.setSummaryVersion("v1");

        MemoryEvidence memory2 = new MemoryEvidence();
        memory2.setMemoryId("mem-2");

        Citation citation = new Citation();
        citation.setType("MEMORY");
        citation.setRefId("ref-1");
        citation.setLabel("label");

        EvidencePack pack = new EvidencePack();
        pack.setToolCalls(List.of(toolCallEvidence));
        pack.setMemoriesUsed(List.of(memory1, memory2));
        pack.setCitations(List.of(citation));

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
        assertEquals(stats.getToolCallsCount(), payload.get("evidenceToolCallsCount"));
        assertEquals(stats.getMemoriesCount(), payload.get("evidenceMemoriesCount"));
        assertEquals(stats.getCitationsCount(), payload.get("evidenceCitationsCount"));
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
        assertEquals(0, payload.get("evidenceToolCallsCount"));
        assertEquals(0, payload.get("evidenceMemoriesCount"));
        assertEquals(0, payload.get("evidenceCitationsCount"));
        assertEquals(0, payload.get("evidenceApproxChars"));
        assertNull(payload.get("evidencePackVersion"));
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
