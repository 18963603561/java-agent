package com.example.agent.runtime.raw;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 内存原始结果存储实现，用于生成稳定 rawRef。
 */
@Component
public class InMemoryRawResultStore implements RawResultStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public InMemoryRawResultStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RawRef store(String source, Object payload, String mediaType) {
        String key = "raw:" + (source == null ? "unknown" : source) + ":" + UUID.randomUUID();
        String text = serialize(payload);
        store.put(key, text);
        RawRef ref = new RawRef();
        ref.setStore("memory");
        ref.setKey(key);
        ref.setMediaType(mediaType == null ? "application/json" : mediaType);
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize((long) text.length());
        ref.setHash("sha256:" + sha256(text));
        return ref;
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
