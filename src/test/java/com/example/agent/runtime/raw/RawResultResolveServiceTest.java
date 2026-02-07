package com.example.agent.runtime.raw;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import com.example.agent.runtime.raw.store.InMemoryRawResultStore;
import com.example.agent.runtime.raw.store.RedisRawResultStore;
import com.example.agent.runtime.raw.store.SizeAwareRawResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
        RawResultResolveService service = buildService(codec, properties, memStore, null, null, new FileRawStorageSupport());

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

        RawResultResolveService service = buildService(codec, properties, null, null, null, fileSupport);
        RawResultResolveResult result = service.resolve(codec.encode(RawStoreType.FILE, filename));

        assertEquals("file", result.getStoreType());
        assertEquals("resolved_from_file", result.getStoreReason());
        assertEquals("hello-file", result.getPayload());
        assertNotNull(result.getAccessUrl());
        assertTrue(result.getAccessUrl().endsWith(filename));
    }

    @Test
    void resolveShouldFallbackLegacyRawKeyMemFirst() {
        RawRefCodec codec = new RawRefCodec();
        InMemoryRawResultStore memStore = new InMemoryRawResultStore(new ObjectMapper(), codec);
        RawRef rawRef = memStore.store("tool:test", Map.of("v", "mem"), "application/json");

        RedisRawResultStore redisStore = Mockito.mock(RedisRawResultStore.class);
        when(redisStore.load(rawRef.getKey())).thenReturn("{\"v\":\"redis\"}");

        RawStoreProperties properties = baseProperties();
        RawResultResolveService service = buildService(codec, properties, memStore, redisStore, null, new FileRawStorageSupport());

        RawResultResolveResult result = service.resolve(rawRef.getKey());
        assertEquals("mem", result.getStoreType());
        assertEquals("legacy_raw_key_mem", result.getStoreReason());
        assertNotNull(result.getResolvedRefId());
        assertTrue(result.getResolvedRefId().startsWith("rawref:v1:mem:"));
        assertTrue(result.getPayload().contains("\"v\":\"mem\""));
    }

    @Test
    void resolveShouldFallbackLegacyRawKeyRedisWhenMemMissing() {
        RawRefCodec codec = new RawRefCodec();
        RedisRawResultStore redisStore = Mockito.mock(RedisRawResultStore.class);
        when(redisStore.load("raw:legacy:1")).thenReturn("{\"v\":\"redis\"}");

        RawStoreProperties properties = baseProperties();
        RawResultResolveService service = buildService(codec, properties, null, redisStore, null, new FileRawStorageSupport());

        RawResultResolveResult result = service.resolve("raw:legacy:1");
        assertEquals("redis", result.getStoreType());
        assertEquals("legacy_raw_key_redis", result.getStoreReason());
        assertEquals("{\"v\":\"redis\"}", result.getPayload());
        assertTrue(result.getResolvedRefId().startsWith("rawref:v1:redis:"));
    }

    @Test
    void resolveShouldSupportFilePublicUrlInput() {
        RawRefCodec codec = new RawRefCodec();
        FileRawStorageSupport fileSupport = new FileRawStorageSupport();
        RawStoreProperties properties = baseProperties();
        String filename = fileSupport.writeText(properties.getFileBaseDir(), "tool:url", "hello-url");

        RawResultResolveService service = buildService(codec, properties, null, null, null, fileSupport);
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
        RawResultResolveService service = buildService(codec, baseProperties(), null, null, null, new FileRawStorageSupport());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class, () -> service.resolve("invalid-ref"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("RAW_REF_INVALID", exception.getErrorCode());
    }

    @Test
    void resolveShouldThrowNotFoundWhenRefMissing() {
        RawRefCodec codec = new RawRefCodec();
        RawResultResolveService service = buildService(codec, baseProperties(), null, null, null, new FileRawStorageSupport());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class, () -> service.resolve("raw:missing:1"));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("RAW_REF_NOT_FOUND", exception.getErrorCode());
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

        RawResultResolveService service = buildService(codec, properties, null, null, sizeAwareStore, new FileRawStorageSupport());
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
                                                 InMemoryRawResultStore memStore,
                                                 RedisRawResultStore redisStore,
                                                 SizeAwareRawResultStore sizeAwareStore,
                                                 FileRawStorageSupport fileSupport) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (memStore != null) {
            beanFactory.registerSingleton("inMemoryRawResultStore", memStore);
        }
        if (redisStore != null) {
            beanFactory.registerSingleton("redisRawResultStore", redisStore);
        }
        if (sizeAwareStore != null) {
            beanFactory.registerSingleton("sizeAwareRawResultStore", sizeAwareStore);
        }
        ObjectProvider<InMemoryRawResultStore> memProvider = beanFactory.getBeanProvider(InMemoryRawResultStore.class);
        ObjectProvider<RedisRawResultStore> redisProvider = beanFactory.getBeanProvider(RedisRawResultStore.class);
        ObjectProvider<SizeAwareRawResultStore> sizeAwareProvider = beanFactory.getBeanProvider(SizeAwareRawResultStore.class);
        return new RawResultResolveService(codec, properties, memProvider, redisProvider, sizeAwareProvider, fileSupport);
    }
}

