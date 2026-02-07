package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.*;
import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存原始结果存储实现。
 *
 * <p>用途：在内存中保存原始文本，并产出统一 rawref 引用标识。
 */
@Component
@ConditionalOnProperty(prefix = "agent.runtime.raw", name = "store-mode", havingValue = "mem")
public class InMemoryRawResultStore implements RawResultStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRawResultStore.class);

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RawRefCodec rawRefCodec;

    public InMemoryRawResultStore(ObjectMapper objectMapper, RawRefCodec rawRefCodec) {
        this.objectMapper = objectMapper;
        this.rawRefCodec = rawRefCodec;
    }

    @Override
    public RawRef store(String source, Object payload, String mediaType) {
        long startNs = System.nanoTime();
        String safeSource = normalizeSource(source);
        String key = "raw:" + safeSource + ":" + UUID.randomUUID();
        String text = serialize(payload);
        store.put(key, text);
        RawRef ref = new RawRef();
        ref.setStore(RawStoreType.MEM.getCode());
        ref.setKey(key);
        ref.setRefId(rawRefCodec.encode(RawStoreType.MEM, key));
        ref.setStoreReason("explicit_mem_mode");
        ref.setMediaType(mediaType == null ? "application/json" : mediaType);
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize((long) text.getBytes(StandardCharsets.UTF_8).length);
        ref.setHash("sha256:" + sha256(text));
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.debug("内存原始结果保存完成, source={}, key={}, refId={}, bytes={}, durationMs={}",
                safeSource,
                key,
                ref.getRefId(),
                ref.getSize(),
                durationMs);
        return ref;
    }

    /**
     * 按 key 读取内存存储文本。
     *
     * @param key 存储 key
     * @return 文本内容，不存在返回 null
     */
    public String load(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return store.get(key.trim());
    }

    /**
     * 按 refId 读取内存存储文本。
     *
     * @param refId 统一引用标识
     * @return 文本内容，不存在返回 null
     */
    public String loadByRefId(String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        if (!rawRefCodec.isRawRef(refId)) {
            return null;
        }
        ParsedRawRef parsed = rawRefCodec.parse(refId);
        if (!RawStoreType.MEM.equals(parsed.storeType())) {
            return null;
        }
        return store.get(parsed.id());
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

