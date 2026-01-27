package com.example.agent.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import jakarta.annotation.PostConstruct;

/**
 * 基于 Qdrant 的向量存储实现。
 */
@Component
@ConditionalOnProperty(prefix = "agent.memory.vector", name = "enabled", havingValue = "true")
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final WebClient webClient;
    private final MemoryVectorProperties properties;

    public QdrantVectorStore(WebClient.Builder builder, MemoryVectorProperties properties) {
        this.properties = properties;
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    @PostConstruct
    public void ensureCollection() {
        String collection = properties.getCollection();
        try {
            webClient.get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .toBodilessEntity()
                    .block(timeout());
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                createCollection(collection);
            } else {
                log.error("Qdrant 集合检查失败, collection={}, status={}", collection, ex.getStatusCode(), ex);
            }
        } catch (Exception ex) {
            log.error("Qdrant 集合检查异常, collection={}", collection, ex);
        }
    }

    @Override
    public void upsert(String tenantId, MemoryRecord record, List<Float> embedding) {
        if (record == null || embedding == null || embedding.isEmpty()) {
            return;
        }
        String collection = properties.getCollection();
        Map<String, Object> payload = buildPayload(tenantId, record);
        Map<String, Object> point = new HashMap<>();
        point.put("id", record.getMemoryId());
        point.put("vector", embedding);
        point.put("payload", payload);
        Map<String, Object> body = Map.of("points", List.of(point));
        try {
            webClient.put()
                    .uri("/collections/{collection}/points", collection)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(timeout());
            log.debug("Qdrant 写入成功, tenantId={}, memoryId={}", tenantId, record.getMemoryId());
        } catch (Exception ex) {
            log.error("Qdrant 写入失败, tenantId={}, memoryId={}", tenantId, record.getMemoryId(), ex);
        }
    }

    @Override
    public List<MemoryRecord> search(String tenantId, String sessionId, List<Float> embedding, int limit) {
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        String collection = properties.getCollection();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", embedding);
        body.put("limit", limit > 0 ? limit : properties.getTopK());
        body.put("with_payload", true);
        if (properties.getScoreThreshold() > 0) {
            body.put("score_threshold", properties.getScoreThreshold());
        }
        Map<String, Object> filter = buildFilter(tenantId, sessionId);
        if (!filter.isEmpty()) {
            body.put("filter", filter);
        }
        try {
            Map<?, ?> response = webClient.post()
                    .uri("/collections/{collection}/points/search", collection)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout());
            return parseSearchResponse(response);
        } catch (Exception ex) {
            log.error("Qdrant 搜索失败, tenantId={}, sessionId={}", tenantId, sessionId, ex);
            return List.of();
        }
    }

    private void createCollection(String collection) {
        Map<String, Object> vectors = Map.of(
                "size", Math.max(1, properties.getDimension()),
                "distance", "Cosine"
        );
        Map<String, Object> body = Map.of("vectors", vectors);
        try {
            webClient.put()
                    .uri("/collections/{collection}", collection)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(timeout());
            log.info("Qdrant 集合创建成功, collection={}", collection);
        } catch (Exception ex) {
            log.error("Qdrant 集合创建失败, collection={}", collection, ex);
        }
    }

    private Map<String, Object> buildPayload(String tenantId, MemoryRecord record) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("memory_id", record.getMemoryId());
        payload.put("session_id", record.getSessionId());
        payload.put("task_id", record.getTaskId());
        payload.put("content", record.getContent());
        payload.put("summary", record.getSummary());
        payload.put("layer", record.getLayer());
        payload.put("tenant_id", tenantId);
        if (record.getCreatedAt() != null) {
            payload.put("created_at", record.getCreatedAt().toString());
        }
        return payload;
    }

    private Map<String, Object> buildFilter(String tenantId, String sessionId) {
        List<Map<String, Object>> must = new ArrayList<>();
        if (tenantId != null) {
            must.add(Map.of("key", "tenant_id", "match", Map.of("value", tenantId)));
        }
        if (sessionId != null) {
            must.add(Map.of("key", "session_id", "match", Map.of("value", sessionId)));
        }
        if (must.isEmpty()) {
            return Map.of();
        }
        return Map.of("must", must);
    }

    @SuppressWarnings("unchecked")
    private List<MemoryRecord> parseSearchResponse(Map<?, ?> response) {
        if (response == null) {
            return List.of();
        }
        Object resultObj = response.get("result");
        if (!(resultObj instanceof List<?> results)) {
            return List.of();
        }
        List<MemoryRecord> records = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object payloadObj = itemMap.get("payload");
            if (!(payloadObj instanceof Map<?, ?> payload)) {
                continue;
            }
            MemoryRecord record = new MemoryRecord();
            record.setMemoryId(valueAsString(payload.get("memory_id")));
            record.setSessionId(valueAsString(payload.get("session_id")));
            record.setTaskId(valueAsString(payload.get("task_id")));
            record.setContent(valueAsString(payload.get("content")));
            record.setSummary(valueAsString(payload.get("summary")));
            record.setLayer(valueAsString(payload.get("layer")));
            record.setTenantId(valueAsString(payload.get("tenant_id")));
            String createdAt = valueAsString(payload.get("created_at"));
            if (createdAt != null) {
                try {
                    record.setCreatedAt(Instant.parse(createdAt));
                } catch (Exception ignore) {
                    record.setCreatedAt(null);
                }
            }
            records.add(record);
        }
        return records;
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
    }
}
