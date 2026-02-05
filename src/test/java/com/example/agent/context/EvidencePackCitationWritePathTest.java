package com.example.agent.context;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.research.ResearchCitation;
import com.example.agent.streaming.ContextEventPublisher;
import com.example.agent.streaming.ContextSnapshotStage;
import com.example.agent.streaming.EventStreamService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EvidencePackCitationWritePathTest {

    @Test
    void researchCitationsAppendToEvidencePackAndPayload() {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        EvidencePackService service = new EvidencePackService(metricsPublisher);

        EvidencePack pack = service.createPack("t1", "wf-1", "snap-1");
        ResearchCitation citationA = new ResearchCitation();
        citationA.setSource("https://example.com/a");
        citationA.setSnippet("a".repeat(300));
        citationA.setFetchedAt(Instant.now());

        ResearchCitation citationB = new ResearchCitation();
        citationB.setSource("web");
        citationB.setSnippet("short label");
        citationB.setFetchedAt(Instant.now());

        service.appendResearchCitations(pack, "step-r", List.of(citationA, citationB), "t1", "wf-1");

        assertNotNull(pack.getEvidences());
        assertEquals(2, pack.getEvidences().size());
        assertEquals(EvidenceType.RESEARCH, pack.getEvidences().get(0).getType());
        assertEquals(2, pack.getStats().getResearchCount());

        ContextSnapshot snapshot = new ContextSnapshot();
        WorkingMemory memory = new WorkingMemory();
        memory.setEvidencePack(pack);
        snapshot.setWorkingMemory(memory);

        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher, eventStreamService, metricsPublisher);

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");
        publisher.publishSnapshotStage(tenantContext, "wf-1", new AtomicLong(0), snapshot, "snap-1",
                null, null, null, null, ContextSnapshotStage.PLAN_ASSEMBLED, null, null);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_SNAPSHOT_STAGE);
        assertNotNull(event);
        Object countValue = event.getPayload().get("evidenceResearchCount");
        assertNotNull(countValue);
        assertEquals(2, ((Number) countValue).intValue());
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        private final List<StreamEvent> events = new java.util.ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
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
