package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.RedisRawResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisRawResultStore 行为测试。
 */
class RedisRawResultStoreTest {

    @Test
    void storeShouldWriteRedisAndBuildRef() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);

        RawStoreProperties properties = new RawStoreProperties();
        properties.setRedisKeyPrefix("raw");
        properties.setRedisTtlSeconds(120);

        RedisRawResultStore store = new RedisRawResultStore(template,
                new ObjectMapper(),
                properties,
                new RawRefCodec());

        RawRef ref = store.store("model:planner", Map.of("answer", "ok"), "application/json");

        assertNotNull(ref);
        assertEquals("redis", ref.getStore());
        assertNotNull(ref.getKey());
        assertTrue(ref.getKey().startsWith("raw:model:planner:"));
        assertNotNull(ref.getRefId());
        assertTrue(ref.getRefId().startsWith("rawref:v1:redis:"));
        assertEquals("default_redis", ref.getStoreReason());
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void loadByRefIdShouldReadRedisValue() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("stored-json");

        RawStoreProperties properties = new RawStoreProperties();
        RedisRawResultStore store = new RedisRawResultStore(template,
                new ObjectMapper(),
                properties,
                new RawRefCodec());

        String value = store.loadByRefId("rawref:v1:redis:raw:model:planner:1");
        assertEquals("stored-json", value);
    }
}
