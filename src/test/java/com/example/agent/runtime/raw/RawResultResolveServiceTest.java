package com.example.agent.runtime.raw;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.FileRawResultStore;
import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import com.example.agent.runtime.raw.store.InMemoryRawResultStore;
import com.example.agent.runtime.raw.store.RawResultStore;
import com.example.agent.runtime.raw.store.RawResultStoreRegistry;
import com.example.agent.runtime.raw.store.SizeAwareRawResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RawResultResolveService 统一提取测试。
 */
class RawResultResolveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveShouldReadFromMemWhenRawRef() {
        RawRefCodec codec = new RawRefCodec();
        InMemoryRawResultStore memStore = new InMemoryRawResultStore(new ObjectMapper(), codec);
        RawRef rawRef = memStore.store("tool:test", Map.of("k", 1), "application/json");

        RawStoreProperties properties = baseProperties();
        RawResultResolveService service = buildService(codec, properties, memStore);

        RawResultResolveResult result = service.resolve(rawRef.getRefId());
        assertEquals(rawRef.getRefId(), result.getResolvedRefId());
        assertEquals("mem", result.getStoreType());
        assertEquals("resolved_from_mem", result.getStoreReason());
        assertNotNull(result.getPayload());
        assertTrue(result.getPayload().contains("\"k\":1"));
    }

    @Test
    void resolveShouldReadFromFileWhenRawRef() {
        RawRefCodec codec = new RawRefCodec();
        FileRawStorageSupport fileSupport = new FileRawStorageSupport();
        RawStoreProperties properties = baseProperties();
        String filename = fileSupport.writeText(properties.getFileBaseDir(), "tool:file", "hello-file");

        FileRawResultStore fileStore = new FileRawResultStore(properties, codec, fileSupport);
        RawResultResolveService service = buildService(codec, properties, fileStore);
        RawResultResolveResult result = service.resolve(codec.encode(RawStoreType.FILE, filename));

        assertEquals("file", result.getStoreType());
        assertEquals("resolved_from_file", result.getStoreReason());
        assertEquals("hello-file", result.getPayload());
        assertNotNull(result.getAccessUrl());
        assertTrue(result.getAccessUrl().endsWith(filename));
    }

    @Test
    void resolveShouldSupportFilePublicUrlInput() {
        RawRefCodec codec = new RawRefCodec();
        FileRawStorageSupport fileSupport = new FileRawStorageSupport();
        RawStoreProperties properties = baseProperties();
        String filename = fileSupport.writeText(properties.getFileBaseDir(), "tool:url", "hello-url");

        FileRawResultStore fileStore = new FileRawResultStore(properties, codec, fileSupport);
        RawResultResolveService service = buildService(codec, properties, fileStore);
        String fileUrl = properties.getFilePublicBaseUrl() + "/" + filename;

        RawResultResolveResult result = service.resolve(fileUrl);
        assertEquals("file", result.getStoreType());
        assertEquals("resolved_from_file", result.getStoreReason());
        assertEquals("hello-url", result.getPayload());
        assertNotNull(result.getResolvedRefId());
        assertTrue(result.getResolvedRefId().startsWith("rawref:v1:file:"));
    }

    @Test
    void resolveShouldThrowBadRequestWhenRefInvalid() {
        RawRefCodec codec = new RawRefCodec();
        RawStoreProperties properties = baseProperties();
        RawResultResolveService service = buildService(codec, properties,
                new InMemoryRawResultStore(new ObjectMapper(), codec));

        ErrorCodeException exception = assertThrows(ErrorCodeException.class, () -> service.resolve("invalid-ref"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("RAW_REF_INVALID", exception.getErrorCode());
    }

    @Test
    void resolveShouldThrowNotFoundWhenRefMissing() {
        RawRefCodec codec = new RawRefCodec();
        RawStoreProperties properties = baseProperties();
        RawResultResolveService service = buildService(codec, properties,
                new InMemoryRawResultStore(new ObjectMapper(), codec));

        ErrorCodeException invalid = assertThrows(ErrorCodeException.class, () -> service.resolve("raw:missing:1"));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("RAW_REF_INVALID", invalid.getErrorCode());

        ErrorCodeException notFound = assertThrows(ErrorCodeException.class,
                () -> service.resolve("rawref:v1:mem:raw:missing:1"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("RAW_REF_NOT_FOUND", notFound.getErrorCode());
    }

    @Test
    void resolveShouldReadMemFromSizeAwareStore() {
        RawRefCodec codec = new RawRefCodec();
        RawStoreProperties properties = baseProperties();
        properties.setSizeAwareThresholdBytes(1024 * 1024);
        SizeAwareRawResultStore sizeAwareStore = new SizeAwareRawResultStore(
                new ObjectMapper(),
                properties,
                codec,
                new FileRawStorageSupport()
        );
        RawRef rawRef = sizeAwareStore.store("tool:size", Map.of("x", 1), "application/json");

        RawResultResolveService service = buildService(codec, properties, sizeAwareStore);
        RawResultResolveResult result = service.resolve(rawRef.getRefId());

        assertEquals("mem", result.getStoreType());
        assertEquals("resolved_from_mem", result.getStoreReason());
        assertTrue(result.getPayload().contains("\"x\":1"));
    }

    private RawStoreProperties baseProperties() {
        RawStoreProperties properties = new RawStoreProperties();
        properties.setFileBaseDir(tempDir.toString());
        properties.setFilePublicBaseUrl("http://127.0.0.1:8080/api/v1/raw/files");
        return properties;
    }

    private RawResultResolveService buildService(RawRefCodec codec,
                                                 RawStoreProperties properties,
                                                 RawResultStore... stores) {
        RawResultStoreRegistry registry = new RawResultStoreRegistry(
                stores == null ? List.of() : List.of(stores),
                codec
        );
        return new RawResultResolveService(codec, properties, registry, new FileRawStorageSupport());
    }
}
