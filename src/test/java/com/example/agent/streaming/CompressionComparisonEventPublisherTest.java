package com.example.agent.streaming;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.payload.ContextEventPublisher;
import com.example.agent.streaming.payload.ContextSnapshotStage;
import com.example.agent.streaming.sse.EventStreamService;
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

/**
 * 压缩对比事件发布测试。
 */
class CompressionComparisonEventPublisherTest {

    @Test
    void shouldPublishComparisonEventWhenDualTrackEnabled() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ContextEventPublisher publisher = new ContextEventPublisher(eventPublisher,
                eventStreamService,
                new MetricsPublisher(new SimpleMeterRegistry()));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snap-compare-1");
        snapshot.setWorkingMemory(new WorkingMemory());

        ContextCompressionResult compressionResult = new ContextCompressionResult();
        compressionResult.setTriggered(true);
        compressionResult.setDualTrackEnabled(true);
        compressionResult.setRolloutVersion("v2");
        compressionResult.setPrimarySource("rule");
        compressionResult.setShadowSource("llm");
        compressionResult.setWinnerSource("llm");
        compressionResult.setRollbackReason("QUALITY_SCORE_BELOW_THRESHOLD");
        compressionResult.setComparisonRecordId("cmp-1");

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");
        publisher.publishSnapshotStage(tenantContext,
                "wf-compare-1",
                new AtomicLong(0),
                snapshot,
                snapshot.getSnapshotId(),
                null,
                null,
                compressionResult,
                null,
                ContextSnapshotStage.CONTEXT_COMPRESSED,
                100,
                80);

        StreamEvent event = eventPublisher.findFirst(EventType.CONTEXT_COMPRESSION_COMPARISON);
        assertNotNull(event);
        Map<String, Object> payload = event.getPayload();
        assertEquals("v2", payload.get("rolloutVersion"));
        assertEquals("rule", payload.get("primarySource"));
        assertEquals("llm", payload.get("shadowSource"));
        assertEquals("llm", payload.get("winner"));
        assertEquals("QUALITY_SCORE_BELOW_THRESHOLD", payload.get("rollbackReason"));
        assertEquals("cmp-1", payload.get("comparisonRecordId"));
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
            // 忽略 ApplicationEvent 分支。
        }

        public StreamEvent findFirst(EventType type) {
            return events.stream()
                    .filter(event -> event.getType() == type)
                    .findFirst()
                    .orElse(null);
        }
    }
}

