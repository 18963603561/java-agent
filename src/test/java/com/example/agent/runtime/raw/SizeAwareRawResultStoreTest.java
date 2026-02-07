package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import com.example.agent.runtime.raw.store.SizeAwareRawResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SizeAwareRawResultStore 阈值分流测试。
 */
class SizeAwareRawResultStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldRouteToMemWhenPayloadSmall() {
        RawStoreProperties properties = new RawStoreProperties();
        properties.setSizeAwareThresholdBytes(1024 * 1024);
        properties.setFileBaseDir(tempDir.toString());
        properties.setFilePublicBaseUrl("http://127.0.0.1:8080/api/v1/raw/files");

        SizeAwareRawResultStore store = new SizeAwareRawResultStore(
                new ObjectMapper(),
                properties,
                new RawRefCodec(),
                new FileRawStorageSupport()
        );

        RawRef ref = store.store("tool:small", Map.of("k", "v"), "application/json");
        assertNotNull(ref);
        assertEquals("mem", ref.getStore());
        assertEquals("size_le_1mb_mem", ref.getStoreReason());
        assertTrue(ref.getRefId().startsWith("rawref:v1:mem:"));

        String loaded = store.loadByRefId(ref.getRefId());
        assertNotNull(loaded);
        assertTrue(loaded.contains("\"k\":\"v\""));
    }

    @Test
    void storeShouldRouteToFileWhenPayloadLarge() {
        RawStoreProperties properties = new RawStoreProperties();
        properties.setSizeAwareThresholdBytes(64);
        properties.setFileBaseDir(tempDir.toString());
        properties.setFilePublicBaseUrl("http://127.0.0.1:8080/api/v1/raw/files");

        SizeAwareRawResultStore store = new SizeAwareRawResultStore(
                new ObjectMapper(),
                properties,
                new RawRefCodec(),
                new FileRawStorageSupport()
        );

        String large = "x".repeat(512);
        RawRef ref = store.store("tool:large", Map.of("text", large), "application/json");

        assertNotNull(ref);
        assertEquals("file", ref.getStore());
        assertEquals("size_gt_1mb_file", ref.getStoreReason());
        assertTrue(ref.getRefId().startsWith("rawref:v1:file:"));
        assertNotNull(ref.getAccessUrl());
        assertTrue(ref.getAccessUrl().contains("/api/v1/raw/files/"));

        String loaded = store.loadByRefId(ref.getRefId());
        assertNotNull(loaded);
        assertTrue(loaded.contains(large));
    }
}

