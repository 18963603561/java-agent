package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.RawStoreProperties;
import com.example.agent.runtime.raw.RawStoreType;
import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 文件原始结果存储实现。
 *
 * <p>用途：将原始文本写入本地文件并支持统一引用读取。
 */
@Component
@ConditionalOnProperty(prefix = "agent.runtime.raw", name = "store-mode", havingValue = "file")
public class FileRawResultStore implements RawResultStore {

    private static final Logger log = LoggerFactory.getLogger(FileRawResultStore.class);

    private final RawStoreProperties properties;
    private final RawRefCodec rawRefCodec;
    private final FileRawStorageSupport fileRawStorageSupport;

    public FileRawResultStore(RawStoreProperties properties,
                              RawRefCodec rawRefCodec,
                              FileRawStorageSupport fileRawStorageSupport) {
        this.properties = properties;
        this.rawRefCodec = rawRefCodec;
        this.fileRawStorageSupport = fileRawStorageSupport;
    }

    @Override
    public RawRef store(String source, Object payload, String mediaType) {
        String text = payload == null ? "null" : String.valueOf(payload);
        long bytes = text.getBytes(StandardCharsets.UTF_8).length;
        String filename = fileRawStorageSupport.writeText(properties.getFileBaseDir(), source, text);
        RawRef ref = new RawRef();
        ref.setStore(RawStoreType.FILE.getCode());
        ref.setKey(filename);
        ref.setRefId(rawRefCodec.encode(RawStoreType.FILE, filename));
        ref.setAccessUrl(fileRawStorageSupport.buildAccessUrl(properties.getFilePublicBaseUrl(), filename));
        ref.setStoreReason("explicit_file_mode");
        ref.setMediaType(mediaType == null ? "text/plain" : mediaType);
        ref.setCreatedAt(Instant.now().toString());
        ref.setSize(bytes);
        ref.setHash("sha256:" + sha256(text));
        log.info("文件原始结果保存完成, source={}, filename={}, refId={}, bytes={}",
                source,
                filename,
                ref.getRefId(),
                bytes);
        return ref;
    }

    @Override
    public RawStoreType storeType() {
        return RawStoreType.FILE;
    }

    @Override
    public String loadByRefId(String refId) {
        if (refId == null || refId.isBlank() || !rawRefCodec.isRawRef(refId)) {
            return null;
        }
        ParsedRawRef parsed = rawRefCodec.parse(refId);
        if (!supports(parsed.storeType())) {
            return null;
        }
        return loadByStoreId(parsed.id());
    }

    @Override
    public boolean supports(RawStoreType storeType) {
        return storeType == RawStoreType.FILE || storeType == RawStoreType.TXT;
    }

    @Override
    public String loadByStoreId(String storeId) {
        if (storeId == null || storeId.isBlank()) {
            return null;
        }
        return fileRawStorageSupport.readText(properties.getFileBaseDir(), storeId.trim());
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

