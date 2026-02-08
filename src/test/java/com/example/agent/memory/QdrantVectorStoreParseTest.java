package com.example.agent.memory;

import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.config.MemoryVectorProperties;
import com.example.agent.capabilities.memory.vector.QdrantVectorStore;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantVectorStoreParseTest {

    @Test
    void parseSearchResponseShouldMapTypedPayloadAndIgnoreBadInstant() throws Exception {
        MemoryVectorProperties properties = new MemoryVectorProperties();
        properties.setBaseUrl("http://localhost:6333");
        QdrantVectorStore store = new QdrantVectorStore(WebClient.builder(), properties);

        Object payload = buildPayload(
                "m1",
                "s1",
                "t1",
                "content-a",
                "summary-a",
                "recent",
                "tenant-a",
                Instant.parse("2026-02-08T10:15:30Z").toString(),
                "not-a-time");
        Object point = buildPoint(payload);
        Object response = buildResponse(List.of(point));

        Method parseMethod = QdrantVectorStore.class.getDeclaredMethod(
                "parseSearchResponse", response.getClass());
        parseMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MemoryRecord> records = (List<MemoryRecord>) parseMethod.invoke(store, response);

        assertEquals(1, records.size());
        MemoryRecord first = records.get(0);
        assertEquals("m1", first.getMemoryId());
        assertEquals("s1", first.getSessionId());
        assertEquals("tenant-a", first.getTenantId());
        assertNotNull(first.getCreatedAt());
        assertNull(first.getExpiresAt());
    }

    @Test
    void parseSearchResponseShouldReturnEmptyWhenResultMissing() throws Exception {
        MemoryVectorProperties properties = new MemoryVectorProperties();
        properties.setBaseUrl("http://localhost:6333");
        QdrantVectorStore store = new QdrantVectorStore(WebClient.builder(), properties);

        Object response = buildResponse(List.of());
        Method parseMethod = QdrantVectorStore.class.getDeclaredMethod(
                "parseSearchResponse", response.getClass());
        parseMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MemoryRecord> records = (List<MemoryRecord>) parseMethod.invoke(store, response);

        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    private Object buildResponse(List<Object> points) throws Exception {
        Class<?> responseClass = Class.forName(
                "com.example.agent.capabilities.memory.vector.QdrantVectorStore$QdrantSearchResponse");
        var constructor = responseClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object response = constructor.newInstance();
        var resultField = responseClass.getDeclaredField("result");
        resultField.setAccessible(true);
        resultField.set(response, points);
        return response;
    }

    private Object buildPoint(Object payload) throws Exception {
        Class<?> pointClass = Class.forName(
                "com.example.agent.capabilities.memory.vector.QdrantVectorStore$QdrantSearchPoint");
        var constructor = pointClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object point = constructor.newInstance();
        var payloadField = pointClass.getDeclaredField("payload");
        payloadField.setAccessible(true);
        payloadField.set(point, payload);
        return point;
    }

    private Object buildPayload(String memoryId,
                                String sessionId,
                                String taskId,
                                String content,
                                String summary,
                                String layer,
                                String tenantId,
                                String createdAt,
                                String expiresAt) throws Exception {
        Class<?> payloadClass = Class.forName(
                "com.example.agent.capabilities.memory.vector.QdrantVectorStore$QdrantPayload");
        var constructor = payloadClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object payload = constructor.newInstance();
        setField(payloadClass, payload, "memoryId", memoryId);
        setField(payloadClass, payload, "sessionId", sessionId);
        setField(payloadClass, payload, "taskId", taskId);
        setField(payloadClass, payload, "content", content);
        setField(payloadClass, payload, "summary", summary);
        setField(payloadClass, payload, "layer", layer);
        setField(payloadClass, payload, "tenantId", tenantId);
        setField(payloadClass, payload, "createdAt", createdAt);
        setField(payloadClass, payload, "expiresAt", expiresAt);
        return payload;
    }

    private void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

