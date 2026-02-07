package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.*;
import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 原始结果存储实现。
 *
 * <p>用途：默认将原始数据序列化后存入 Redis，并产出统一 rawref 标识。
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "agent.runtime.raw", name = "store-mode", havingValue = "redis", matchIfMissing = true)
public class RedisRawResultStore implements RawResultStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRawResultStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RawStoreProperties properties;
    private final RawRefCodec rawRefCodec;

    public RedisRawResultStore(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               RawStoreProperties properties,
                               RawRefCodec rawRefCodec) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.rawRefCodec = rawRefCodec;
    }

    @Override
    public RawRef store(String source, Object payload, String mediaType) {
        long startNs = System.nanoTime();
        String safeSource = normalizeSource(source);
        String key = buildRedisKey(safeSource);
        String text = serialize(payload);
        Duration ttl = Duration.ofSeconds(Math.max(1, properties.getRedisTtlSeconds()));
        redisTemplate.opsForValue().set(key, text, ttl);
        RawRef ref = new RawRef();
        ref.setStore(RawStoreType.REDIS.getCode());
        ref.setKey(key);
        ref.setRefId(rawRefCodec.encode(RawStoreType.REDIS, key));
        ref.setStoreReason("default_redis");
        ref.setMediaType(mediaType == null ? "application/json" : mediaType);
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize((long) text.getBytes(StandardCharsets.UTF_8).length);
        ref.setHash("sha256:" + sha256(text));
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("原始结果写入Redis完成, source={}, key={}, refId={}, bytes={}, ttlSeconds={}, durationMs={}",
                safeSource,
                key,
                ref.getRefId(),
                ref.getSize(),
                ttl.getSeconds(),
                durationMs);
        return ref;
    }

    @Override
    public RawStoreType storeType() {
        return RawStoreType.REDIS;
    }

    /**
     * 按 redis key 读取文本。
     *
     * @param key redis key
     * @return 文本内容，不存在返回 null
     */
    public String load(String key) {
        return loadByStoreId(key);
    }

    /**
     * 按 rawref 引用读取文本。
     *
     * @param refId 统一引用
     * @return 文本内容，不存在返回 null
     */
    @Override
    public String loadByRefId(String refId) {
        if (refId == null || refId.isBlank() || !rawRefCodec.isRawRef(refId)) {
            return null;
        }
        ParsedRawRef parsed = rawRefCodec.parse(refId);
        if (!RawStoreType.REDIS.equals(parsed.storeType())) {
            return null;
        }
        return load(parsed.id());
    }

    @Override
    public String loadByStoreId(String storeId) {
        if (storeId == null || storeId.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(storeId.trim());
    }

    private String buildRedisKey(String source) {
        String prefix = properties.getRedisKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "raw";
        }
        return prefix + ":" + source + ":" + UUID.randomUUID();
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return source.trim();
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return String.valueOf(payload);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
