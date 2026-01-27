package com.example.agent.agentcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 工具缓存，用于缓存工具执行结果并提供简单过期机制。
 */
@Component
public class ToolCache {

    private static final Logger log = LoggerFactory.getLogger(ToolCache.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    @Value("${agent.tool.cache.redis-enabled:false}")
    private boolean redisEnabled;

    @Value("${agent.tool.cache.redis-key-prefix:toolcache}")
    private String redisKeyPrefix;

    @Value("${agent.tool.cache.ttl-seconds:300}")
    private long defaultTtlSeconds;

    public ToolCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider, ObjectMapper objectMapper) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取缓存对象（不校验过期）。
     *
     * @param key 缓存键
     * @return 缓存对象
     */
    public Object get(String key) {
        if (useRedis()) {
            return getFromRedis(key);
        }
        CacheEntry entry = cache.get(key);
        return entry == null ? null : entry.value;
    }

    /**
     * 获取缓存对象（带过期校验）。
     *
     * @param key 缓存键
     * @param ttl 过期时间
     * @return 缓存对象
     */
    public Object getIfFresh(String key, Duration ttl) {
        if (useRedis()) {
            return getFromRedis(key);
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return entry.value;
        }
        if (entry.isExpired(ttl)) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 写入缓存对象。
     *
     * @param key 缓存键
     * @param value 缓存对象
     */
    public void put(String key, Object value) {
        put(key, value, null);
    }

    /**
     * 写入缓存对象并指定过期时间。
     *
     * @param key 缓存键
     * @param value 缓存对象
     * @param ttl 过期时间
     */
    public void put(String key, Object value, Duration ttl) {
        if (useRedis()) {
            putToRedis(key, value, ttl);
            return;
        }
        cache.put(key, new CacheEntry(value, Instant.now()));
    }

    /**
     * 移除缓存对象。
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        cache.remove(key);
        if (useRedis()) {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                redisTemplate.delete(buildRedisKey(key));
            }
        }
    }

    private boolean useRedis() {
        return redisEnabled && redisTemplateProvider.getIfAvailable() != null;
    }

    private Object getFromRedis(String key) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(buildRedisKey(key));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            log.warn("工具缓存反序列化失败, key={}", key, ex);
            return null;
        }
    }

    private void putToRedis(String key, Object value, Duration ttl) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            Duration effectiveTtl = ttl != null ? ttl : Duration.ofSeconds(Math.max(0, defaultTtlSeconds));
            if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
                redisTemplate.opsForValue().set(buildRedisKey(key), json);
            } else {
                redisTemplate.opsForValue().set(buildRedisKey(key), json, effectiveTtl);
            }
        } catch (JsonProcessingException ex) {
            log.warn("工具缓存序列化失败, key={}", key, ex);
        }
    }

    private String buildRedisKey(String key) {
        return redisKeyPrefix + ":" + key;
    }

    private static class CacheEntry {
        private final Object value;
        private final Instant storedAt;

        private CacheEntry(Object value, Instant storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }

        private boolean isExpired(Duration ttl) {
            return storedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}