package com.example.agent.streaming.sse;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.http.HttpStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Sinks;

/**
 * 事件流服务，负责订阅与事件分发。
 */
@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);
    private static final Duration STREAM_TTL = Duration.ofHours(24);

    private final Sinks.Many<StreamEvent> sink;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final MetricsPublisher metricsPublisher;
    private final int maxStreamSize;

    private final Map<String, Deque<String>> streamIndex = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();
    private Scheduler validationScheduler = Schedulers.boundedElastic();

    public EventStreamService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                              ObjectMapper objectMapper,
                              MetricsPublisher metricsPublisher,
                              @org.springframework.beans.factory.annotation.Value("${agent.sse.max-stream-size:1024}")
                              int maxStreamSize) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
        this.maxStreamSize = Math.max(64, maxStreamSize);
    }

    /**
     * 订阅事件流。
     *
     * @param request 订阅请求
     * @param tenantContext 租户上下文
     * @return 事件流
     */
    public Flux<StreamEvent> stream(TaskStreamRequest request, TenantContext tenantContext) {
        return Mono.fromRunnable(() -> validateCursor(request, tenantContext))
                .subscribeOn(validationScheduler)
                .thenMany(Mono.fromSupplier(() -> loadHistoryEvents(request, tenantContext))
                        .flatMapMany(history -> {
                            Flux<StreamEvent> live = sink.asFlux()
                                    .filter(event -> tenantContext.getTenantId().equals(event.getTenantId()))
                                    .filter(event -> request.getWorkflowId().equals(event.getWorkflowId()))
                                    .filter(event -> matchTypes(request, event));
                            if (history.isEmpty()) {
                                return live;
                            }
                            long lastSeq = parseSeq(request.getLastEventId());
                            Flux<StreamEvent> filtered = live.filter(event -> isAfterCursor(event, lastSeq));
                            return Flux.fromIterable(history).concatWith(filtered);
                        }));
    }

    /**
     * 处理内部事件并分发到订阅端。
     *
     * @param event 事件对象
     */
    @EventListener
    public void onStreamEvent(StreamEvent event) {
        syncSequenceCounter(event);
        updateIndex(event);
        writeToRedis(event);
        recordLag(event);
        metricsPublisher.increment("event.stream.count");
        sink.tryEmitNext(event);
    }

    /**
     * 获取租户与工作流的序列计数器。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 可复用的序列计数器
     */
    public AtomicLong sequenceCounter(String tenantId, String workflowId) {
        return getSequenceCounter(tenantId, workflowId);
    }

    /**
     * 生成下一个事件序列号。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 序列号
     */
    public long nextSequence(String tenantId, String workflowId) {
        return getSequenceCounter(tenantId, workflowId).incrementAndGet();
    }

    /**
     * 移除租户与工作流的序列计数器。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     */
    public void evictSequence(String tenantId, String workflowId) {
        String indexKey = buildIndexKey(tenantId, workflowId);
        sequenceCounters.remove(indexKey);
        streamIndex.remove(indexKey);
    }

    /**
     * 记录特殊事件，仅用于游标续传校验，不向订阅端广播。
     *
     * @param event 事件对象
     */
    public void recordSyntheticEvent(StreamEvent event) {
        if (event == null) {
            return;
        }
        syncSequenceCounter(event);
        updateIndex(event);
        writeToRedis(event);
    }

    private boolean matchTypes(TaskStreamRequest request, StreamEvent event) {
        if (request.getTypes() == null || request.getTypes().isEmpty()) {
            return true;
        }
        return request.getTypes().contains(event.getType().name());
    }

    private boolean isAfterCursor(StreamEvent event, long lastSeq) {
        if (event == null) {
            return false;
        }
        long seq = event.getSeq();
        if (seq <= 0 && StringUtils.hasText(event.getEventId())) {
            seq = parseSeq(event.getEventId());
        }
        if (lastSeq <= 0) {
            return true;
        }
        return seq > lastSeq;
    }

    private List<StreamEvent> loadHistoryEvents(TaskStreamRequest request, TenantContext tenantContext) {
        if (request == null || tenantContext == null) {
            return List.of();
        }
        String cursor = request.getLastEventId();
        if (!StringUtils.hasText(cursor)) {
            return List.of();
        }
        String tenantId = tenantContext.getTenantId();
        String workflowId = request.getWorkflowId();
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(workflowId)) {
            return List.of();
        }
        long lastSeq = parseSeq(cursor);
        if (lastSeq <= 0) {
            return List.of();
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("Redis not available for stream replay, tenantId={}, workflowId={}", tenantId, workflowId);
            return List.of();
        }
        return readEventsFromRedis(tenantId, workflowId, lastSeq);
    }

    private List<StreamEvent> readEventsFromRedis(String tenantId, String workflowId, long lastSeq) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return List.of();
        }
        String streamKey = "stream:" + workflowId;
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded());
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<StreamEvent> events = new java.util.ArrayList<>();
        for (MapRecord<String, Object, Object> record : records) {
            Object payloadValue = record.getValue().get("event");
            if (!(payloadValue instanceof String payload) || !StringUtils.hasText(payload)) {
                continue;
            }
            try {
                StreamEvent event = objectMapper.readValue(payload, StreamEvent.class);
                if (event.getEventId() != null
                        && tenantId.equals(event.getTenantId())
                        && workflowId.equals(event.getWorkflowId())) {
                    long seq = event.getSeq();
                    if (seq <= 0) {
                        seq = parseSeq(event.getEventId());
                    }
                    if (seq > lastSeq) {
                        events.add(event);
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("Redis stream parse failed, streamKey={}", streamKey, e);
            }
        }
        events.sort((left, right) -> Long.compare(
                left.getSeq() > 0 ? left.getSeq() : parseSeq(left.getEventId()),
                right.getSeq() > 0 ? right.getSeq() : parseSeq(right.getEventId())));
        return events;
    }

    private void validateCursor(TaskStreamRequest request, TenantContext tenantContext) {
        String lastEventId = normalizeEventId(request.getWorkflowId(), request.getLastEventId());
        if (!StringUtils.hasText(lastEventId)) {
            return;
        }
        request.setLastEventId(lastEventId);
        String workflowId = request.getWorkflowId();
        String indexKey = buildIndexKey(tenantContext.getTenantId(), workflowId);
        Deque<String> events = streamIndex.get(indexKey);
        if (events == null || events.isEmpty()) {
            events = reloadIndexFromRedis(tenantContext.getTenantId(), workflowId);
            if (events != null) {
                streamIndex.put(indexKey, events);
            }
        }
        if (events == null || events.isEmpty() || !events.contains(lastEventId)) {
            log.warn("STREAM_GAP detected, tenantId={}, workflowId={}, lastEventId={}",
                    tenantContext.getTenantId(), workflowId, lastEventId);
            throw new com.example.agent.common.error.ErrorCodeException(HttpStatus.CONFLICT,
                    "STREAM_GAP", "STREAM_GAP");
        }
    }

    private void updateIndex(StreamEvent event) {
        String workflowId = event.getWorkflowId();
        String tenantId = event.getTenantId();
        if (workflowId == null || tenantId == null) {
            return;
        }
        String indexKey = buildIndexKey(tenantId, workflowId);
        streamIndex.computeIfAbsent(indexKey, key -> new ArrayDeque<>());
        Deque<String> deque = streamIndex.get(indexKey);
        synchronized (deque) {
            deque.addLast(event.getEventId());
            while (deque.size() > maxStreamSize) {
                deque.removeFirst();
            }
        }
    }

    private String normalizeEventId(String workflowId, String lastEventId) {
        if (!StringUtils.hasText(lastEventId)) {
            return null;
        }
        String trimmed = lastEventId.trim();
        if (trimmed.matches("\\d+")) {
            if (!StringUtils.hasText(workflowId)) {
            throw new com.example.agent.common.error.ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "INVALID_CURSOR", "Invalid cursor");
            }
            return workflowId + ":" + trimmed;
        }
        if (trimmed.contains(":")) {
            return trimmed;
        }
        throw new com.example.agent.common.error.ErrorCodeException(HttpStatus.BAD_REQUEST,
                "INVALID_CURSOR", "Invalid cursor");
    }

    private String buildIndexKey(String tenantId, String workflowId) {
        return tenantId + ":" + workflowId;
    }

    private Deque<String> reloadIndexFromRedis(String tenantId, String workflowId) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("Redis not available for stream cursor validation, workflowId={}", workflowId);
            return null;
        }
        String streamKey = "stream:" + workflowId;
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded());
        if (records == null || records.isEmpty()) {
            return new ArrayDeque<>();
        }
        if (records.size() > maxStreamSize) {
            records = records.subList(records.size() - maxStreamSize, records.size());
        }
        Deque<String> deque = new ArrayDeque<>();
        long maxSeq = 0;
        for (MapRecord<String, Object, Object> record : records) {
            Object payloadValue = record.getValue().get("event");
            if (!(payloadValue instanceof String payload) || !StringUtils.hasText(payload)) {
                continue;
            }
            try {
                StreamEvent event = objectMapper.readValue(payload, StreamEvent.class);
                if (event.getEventId() != null && tenantId.equals(event.getTenantId())) {
                    deque.addLast(event.getEventId());
                    long seq = event.getSeq();
                    if (seq <= 0) {
                        seq = parseSeq(event.getEventId());
                    }
                    if (seq > maxSeq) {
                        maxSeq = seq;
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("Redis stream parse failed, streamKey={}", streamKey, e);
            }
        }
        if (maxSeq > 0) {
            syncSequenceCounter(tenantId, workflowId, maxSeq);
        }
        return deque;
    }

    private void writeToRedis(StreamEvent event) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        Mono.fromRunnable(() -> {
            try {
                String streamKey = "stream:" + event.getStreamId();
                String payload = objectMapper.writeValueAsString(event);
                RecordId recordId = redisTemplate.opsForStream()
                        .add(StreamRecords.newRecord().ofMap(Map.of("event", payload)).withStreamKey(streamKey));
                redisTemplate.opsForStream().trim(streamKey, maxStreamSize);
                redisTemplate.expire(streamKey, STREAM_TTL);
                log.debug("Redis stream append, streamKey={}, recordId={}", streamKey, recordId);
            } catch (JsonProcessingException e) {
                log.error("Redis stream serialize failed, eventId={}", event.getEventId(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private void recordLag(StreamEvent event) {
        if (event.getTimestamp() == null) {
            return;
        }
        long lagMs = Duration.between(event.getTimestamp(), java.time.Instant.now()).toMillis();
        metricsPublisher.recordTime("event.stream.lag.ms", lagMs);
    }

    private AtomicLong getSequenceCounter(String tenantId, String workflowId) {
        return sequenceCounters.computeIfAbsent(buildIndexKey(tenantId, workflowId), key -> new AtomicLong(0));
    }

    private void syncSequenceCounter(StreamEvent event) {
        if (event == null || !StringUtils.hasText(event.getTenantId())
                || !StringUtils.hasText(event.getWorkflowId())) {
            return;
        }
        long seq = event.getSeq();
        if (seq <= 0 && StringUtils.hasText(event.getEventId())) {
            seq = parseSeq(event.getEventId());
        }
        if (seq > 0) {
            syncSequenceCounter(event.getTenantId(), event.getWorkflowId(), seq);
        }
    }

    private void syncSequenceCounter(String tenantId, String workflowId, long seq) {
        if (seq <= 0) {
            return;
        }
        AtomicLong counter = getSequenceCounter(tenantId, workflowId);
        counter.updateAndGet(current -> Math.max(current, seq));
    }

    private long parseSeq(String eventId) {
        int index = eventId.lastIndexOf(':');
        if (index < 0 || index == eventId.length() - 1) {
            return 0;
        }
        try {
            return Long.parseLong(eventId.substring(index + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
