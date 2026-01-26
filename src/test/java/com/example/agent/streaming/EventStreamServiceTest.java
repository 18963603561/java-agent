package com.example.agent.streaming;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventStreamServiceTest {

    @Test
    void resumeWithNumericCursorUsesRedisFallback() throws Exception {
        String workflowId = "workflow-1";
        String tenantId = "tenant-a";
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StreamEvent event = buildEvent(workflowId, tenantId, workflowId + ":5", 5);
        String payload = objectMapper.writeValueAsString(event);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(ops);
        MapRecord record = StreamRecords.newRecord()
                .ofMap(Map.of("event", payload))
                .withStreamKey("stream:" + workflowId);
        when(ops.range(eq("stream:" + workflowId), any()))
                .thenReturn(List.of(record));

        MetricsPublisher metricsPublisher = mock(MetricsPublisher.class);
        EventStreamService service = new EventStreamService(new FixedObjectProvider<>(redisTemplate),
                objectMapper, metricsPublisher);
        ReflectionTestUtils.setField(service, "validationScheduler", Schedulers.immediate());

        TaskStreamRequest request = new TaskStreamRequest();
        request.setWorkflowId(workflowId);
        request.setLastEventId("5");
        TenantContext tenantContext = new TenantContext(tenantId, "user-1", List.of(), "req-1", "trace-1");

        Flux<StreamEvent> flux = service.stream(request, tenantContext);
        StepVerifier.create(flux)
                .expectSubscription()
                .thenCancel()
                .verify();
        assertEquals(workflowId + ":5", request.getLastEventId());
    }

    @Test
    void resumeWithFullCursorUsesRedisFallback() throws Exception {
        String workflowId = "workflow-2";
        String tenantId = "tenant-a";
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StreamEvent event = buildEvent(workflowId, tenantId, workflowId + ":7", 7);
        String payload = objectMapper.writeValueAsString(event);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations ops = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(ops);
        MapRecord record = StreamRecords.newRecord()
                .ofMap(Map.of("event", payload))
                .withStreamKey("stream:" + workflowId);
        when(ops.range(eq("stream:" + workflowId), any()))
                .thenReturn(List.of(record));

        MetricsPublisher metricsPublisher = mock(MetricsPublisher.class);
        EventStreamService service = new EventStreamService(new FixedObjectProvider<>(redisTemplate),
                objectMapper, metricsPublisher);
        ReflectionTestUtils.setField(service, "validationScheduler", Schedulers.immediate());

        TaskStreamRequest request = new TaskStreamRequest();
        request.setWorkflowId(workflowId);
        request.setLastEventId(workflowId + ":7");
        TenantContext tenantContext = new TenantContext(tenantId, "user-1", List.of(), "req-1", "trace-1");

        Flux<StreamEvent> flux = service.stream(request, tenantContext);
        StepVerifier.create(flux)
                .expectSubscription()
                .thenCancel()
                .verify();
        assertEquals(workflowId + ":7", request.getLastEventId());
    }

    @Test
    void tenantIsolationFiltersEvents() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MetricsPublisher metricsPublisher = mock(MetricsPublisher.class);
        EventStreamService service = new EventStreamService(new FixedObjectProvider<>(null),
                objectMapper, metricsPublisher);

        TaskStreamRequest request = new TaskStreamRequest();
        request.setWorkflowId("workflow-3");
        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1");

        StreamEvent tenantBEvent = buildEvent("workflow-3", "tenant-b", "workflow-3:1", 1);
        StreamEvent tenantAEvent = buildEvent("workflow-3", "tenant-a", "workflow-3:2", 2);

        Flux<StreamEvent> flux = service.stream(request, tenantContext);
        StepVerifier.create(flux)
                .then(() -> {
                    service.onStreamEvent(tenantBEvent);
                    service.onStreamEvent(tenantAEvent);
                })
                .expectNextMatches(event -> "tenant-a".equals(event.getTenantId())
                        && "workflow-3:2".equals(event.getEventId()))
                .thenCancel()
                .verify();
    }

    private StreamEvent buildEvent(String workflowId, String tenantId, String eventId, long seq) {
        StreamEvent event = new StreamEvent();
        event.setEventId(eventId);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(EventType.WORKFLOW_STARTED);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantId);
        return event;
    }

    private static class FixedObjectProvider<T> implements ObjectProvider<T> {

        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfAvailable(java.util.function.Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getIfUnique(java.util.function.Supplier<T> defaultSupplier) {
            return value != null ? value : defaultSupplier.get();
        }

        @Override
        public Stream<T> stream() {
            return value == null ? Stream.empty() : Stream.of(value);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
