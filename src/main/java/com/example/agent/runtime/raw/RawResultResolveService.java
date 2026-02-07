package com.example.agent.runtime.raw;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import com.example.agent.runtime.raw.store.InMemoryRawResultStore;
import com.example.agent.runtime.raw.store.RedisRawResultStore;
import com.example.agent.runtime.raw.store.SizeAwareRawResultStore;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 原始结果统一提取服务。
 *
 * <p>用途：统一解析 rawref/raw/file-url 等引用格式，并从对应存储中读取原始内容。
 */
@Service
public class RawResultResolveService {

    private static final Logger log = LoggerFactory.getLogger(RawResultResolveService.class);

    private final RawRefCodec rawRefCodec;
    private final RawStoreProperties properties;
    private final ObjectProvider<InMemoryRawResultStore> inMemoryStoreProvider;
    private final ObjectProvider<RedisRawResultStore> redisStoreProvider;
    private final ObjectProvider<SizeAwareRawResultStore> sizeAwareStoreProvider;
    private final FileRawStorageSupport fileRawStorageSupport;

    public RawResultResolveService(RawRefCodec rawRefCodec,
                                   RawStoreProperties properties,
                                   ObjectProvider<InMemoryRawResultStore> inMemoryStoreProvider,
                                   ObjectProvider<RedisRawResultStore> redisStoreProvider,
                                   ObjectProvider<SizeAwareRawResultStore> sizeAwareStoreProvider,
                                   FileRawStorageSupport fileRawStorageSupport) {
        this.rawRefCodec = rawRefCodec;
        this.properties = properties;
        this.inMemoryStoreProvider = inMemoryStoreProvider;
        this.redisStoreProvider = redisStoreProvider;
        this.sizeAwareStoreProvider = sizeAwareStoreProvider;
        this.fileRawStorageSupport = fileRawStorageSupport;
    }

    /**
     * 统一提取原始结果。
     *
     * @param ref 输入引用
     * @return 提取结果
     */
    public RawResultResolveResult resolve(String ref) {
        if (!StringUtils.hasText(ref)) {
            throw invalidRef("ref 不能为空");
        }
        long startNs = System.nanoTime();
        String normalized = normalizeRef(ref.trim());
        RawResultResolveResult result = new RawResultResolveResult();
        result.setInputRef(ref);
        if (rawRefCodec.isRawRef(normalized)) {
            ParsedRawRef parsed = rawRefCodec.parse(normalized);
            result.setResolvedRefId(normalized);
            result.setStoreType(parsed.storeType().getCode());
            resolveByParsed(parsed, result);
        } else if (normalized.startsWith("raw:")) {
            resolveLegacyRawKey(normalized, result);
        } else {
            throw invalidRef("不支持的引用格式: " + ref);
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("原始结果统一提取完成, inputRef={}, resolvedRefId={}, storeType={}, bytes={}, durationMs={}",
                ref,
                result.getResolvedRefId(),
                result.getStoreType(),
                result.getSize(),
                durationMs);
        return result;
    }

    /**
     * 下载文件内容。
     *
     * @param filename 文件名
     * @return 文件内容
     */
    public String downloadText(String filename) {
        return fileRawStorageSupport.readText(properties.getFileBaseDir(), filename);
    }

    private String normalizeRef(String ref) {
        String baseUrl = properties.getFilePublicBaseUrl();
        if (StringUtils.hasText(baseUrl) && ref.startsWith(baseUrl)) {
            int index = ref.lastIndexOf('/');
            if (index >= 0 && index + 1 < ref.length()) {
                String filename = ref.substring(index + 1);
                return rawRefCodec.encode(RawStoreType.FILE, filename);
            }
        }
        return ref;
    }

    private void resolveByParsed(ParsedRawRef parsed, RawResultResolveResult result) {
        String payload;
        if (RawStoreType.MEM.equals(parsed.storeType())) {
            payload = loadFromMem(parsed.id());
            result.setStoreReason("resolved_from_mem");
        } else if (RawStoreType.REDIS.equals(parsed.storeType())) {
            payload = loadFromRedis(parsed.id());
            result.setStoreReason("resolved_from_redis");
        } else {
            payload = fileRawStorageSupport.readText(properties.getFileBaseDir(), parsed.id());
            result.setStoreReason("resolved_from_file");
            result.setAccessUrl(fileRawStorageSupport.buildAccessUrl(properties.getFilePublicBaseUrl(), parsed.id()));
        }
        applyPayload(result, payload);
    }

    private void resolveLegacyRawKey(String rawKey, RawResultResolveResult result) {
        String payload = loadFromMem(rawKey);
        if (payload != null) {
            result.setStoreType(RawStoreType.MEM.getCode());
            result.setResolvedRefId(rawRefCodec.encode(RawStoreType.MEM, rawKey));
            result.setStoreReason("legacy_raw_key_mem");
            applyPayload(result, payload);
            return;
        }
        payload = loadFromRedis(rawKey);
        if (payload != null) {
            result.setStoreType(RawStoreType.REDIS.getCode());
            result.setResolvedRefId(rawRefCodec.encode(RawStoreType.REDIS, rawKey));
            result.setStoreReason("legacy_raw_key_redis");
            applyPayload(result, payload);
            return;
        }
        throw notFoundRef("未找到引用对应原始数据: " + rawKey);
    }

    private String loadFromMem(String key) {
        InMemoryRawResultStore memStore = inMemoryStoreProvider.getIfAvailable();
        if (memStore != null) {
            String found = memStore.load(key);
            if (found != null) {
                return found;
            }
        }
        SizeAwareRawResultStore sizeAware = sizeAwareStoreProvider.getIfAvailable();
        if (sizeAware != null) {
            return sizeAware.loadByKey(key);
        }
        return null;
    }

    private String loadFromRedis(String key) {
        RedisRawResultStore redisStore = redisStoreProvider.getIfAvailable();
        if (redisStore == null) {
            return null;
        }
        return redisStore.load(key);
    }

    private void applyPayload(RawResultResolveResult result, String payload) {
        if (payload == null) {
            throw notFoundRef("引用存在但原始数据为空");
        }
        result.setMediaType("text/plain");
        result.setPayload(payload);
        result.setSize((long) payload.getBytes(StandardCharsets.UTF_8).length);
    }

    private ErrorCodeException invalidRef(String message) {
        return new ErrorCodeException(HttpStatus.BAD_REQUEST, "RAW_REF_INVALID", message);
    }

    private ErrorCodeException notFoundRef(String message) {
        return new ErrorCodeException(HttpStatus.NOT_FOUND, "RAW_REF_NOT_FOUND", message);
    }
}
