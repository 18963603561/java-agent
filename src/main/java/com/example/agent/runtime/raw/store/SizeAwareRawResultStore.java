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
 * 按大小阈值分流的原始结果存储实现。
 *
 * <p>策略：
 * 1. 文本字节数不超过阈值，写内存并返回 mem 引用；
 * 2. 超过阈值，写本地 txt 文件并返回 file/txt 引用。
 */
@Component
@ConditionalOnProperty(prefix = "agent.runtime.raw", name = "store-mode", havingValue = "size-aware")
public class SizeAwareRawResultStore implements RawResultStore {

    private static final Logger log = LoggerFactory.getLogger(SizeAwareRawResultStore.class);

    private final Map<String, String> memStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RawStoreProperties properties;
    private final RawRefCodec rawRefCodec;
    private final FileRawStorageSupport fileRawStorageSupport;

    public SizeAwareRawResultStore(ObjectMapper objectMapper,
                                   RawStoreProperties properties,
                                   RawRefCodec rawRefCodec,
                                   FileRawStorageSupport fileRawStorageSupport) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.rawRefCodec = rawRefCodec;
        this.fileRawStorageSupport = fileRawStorageSupport;
    }

    @Override
    public RawRef store(String source, Object payload, String mediaType) {
        long startNs = System.nanoTime();
        String text = serialize(payload);
        long bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= Math.max(1, properties.getSizeAwareThresholdBytes())) {
            RawRef ref = storeToMem(source, text, mediaType, bytes);
            log.info("原始结果阈值分流完成, route=mem, source={}, bytes={}, threshold={}, refId={}, durationMs={}",
                    source,
                    bytes,
                    properties.getSizeAwareThresholdBytes(),
                    ref.getRefId(),
                    (System.nanoTime() - startNs) / 1_000_000);
            return ref;
        }
        RawRef ref = storeToFile(source, text, mediaType, bytes);
        log.info("原始结果阈值分流完成, route=file, source={}, bytes={}, threshold={}, refId={}, durationMs={}",
                source,
                bytes,
                properties.getSizeAwareThresholdBytes(),
                ref.getRefId(),
                (System.nanoTime() - startNs) / 1_000_000);
        return ref;
    }

    /**
     * 按统一引用读取内容。
     *
     * @param refId 统一引用标识
     * @return 文本内容，不存在返回 null
     */
    public String loadByRefId(String refId) {
        if (refId == null || refId.isBlank() || !rawRefCodec.isRawRef(refId)) {
            return null;
        }
        ParsedRawRef parsed = rawRefCodec.parse(refId);
        if (RawStoreType.MEM.equals(parsed.storeType())) {
            return memStore.get(parsed.id());
        }
        if (RawStoreType.FILE.equals(parsed.storeType()) || RawStoreType.TXT.equals(parsed.storeType())) {
            return fileRawStorageSupport.readText(properties.getFileBaseDir(), parsed.id());
        }
        return null;
    }

    private RawRef storeToMem(String source, String text, String mediaType, long bytes) {
        String key = "raw:" + normalizeSource(source) + ":" + UUID.randomUUID();
        memStore.put(key, text);
        RawRef ref = new RawRef();
        ref.setStore(RawStoreType.MEM.getCode());
        ref.setKey(key);
        ref.setRefId(rawRefCodec.encode(RawStoreType.MEM, key));
        ref.setStoreReason("size_le_1mb_mem");
        ref.setMediaType(defaultMediaType(mediaType));
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize(bytes);
        ref.setHash("sha256:" + sha256(text));
        return ref;
    }

    private RawRef storeToFile(String source, String text, String mediaType, long bytes) {
        String filename = fileRawStorageSupport.writeText(properties.getFileBaseDir(), source, text);
        RawRef ref = new RawRef();
        ref.setStore(RawStoreType.FILE.getCode());
        ref.setKey(filename);
        ref.setRefId(rawRefCodec.encode(RawStoreType.FILE, filename));
        ref.setAccessUrl(fileRawStorageSupport.buildAccessUrl(properties.getFilePublicBaseUrl(), filename));
        ref.setStoreReason("size_gt_1mb_file");
        ref.setMediaType(defaultMediaType(mediaType));
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize(bytes);
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

    private String defaultMediaType(String mediaType) {
        return mediaType == null ? "application/json" : mediaType;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return source.trim();
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

