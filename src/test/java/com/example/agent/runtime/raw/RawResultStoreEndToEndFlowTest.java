package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.ParsedRawRef;
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
 * 原始结果存储阶段一+阶段二全流程测试。
 */
class RawResultStoreEndToEndFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void fullFlowShouldPassForMemAndFileRoutes() {
        RawRefCodec codec = new RawRefCodec();
        RawStoreProperties properties = new RawStoreProperties();
        properties.setSizeAwareThresholdBytes(128);
        properties.setFileBaseDir(tempDir.toString());
        properties.setFilePublicBaseUrl("http://127.0.0.1:8080/api/v1/raw/files");

        SizeAwareRawResultStore store = new SizeAwareRawResultStore(
                new ObjectMapper(),
                properties,
                codec,
                new FileRawStorageSupport()
        );

        RawRef smallRef = store.store("tool:demo", Map.of("x", 1), "application/json");
        assertEquals("mem", smallRef.getStore());
        ParsedRawRef smallParsed = codec.parse(smallRef.getRefId());
        assertEquals(RawStoreType.MEM, smallParsed.storeType());
        String smallPayload = store.loadByRefId(smallRef.getRefId());
        assertTrue(smallPayload.contains("\"x\":1"));

        RawRef largeRef = store.store("tool:demo", Map.of("text", "y".repeat(1024)), "application/json");
        assertEquals("file", largeRef.getStore());
        assertNotNull(largeRef.getAccessUrl());
        ParsedRawRef largeParsed = codec.parse(largeRef.getRefId());
        assertEquals(RawStoreType.FILE, largeParsed.storeType());
        String largePayload = store.loadByRefId(largeRef.getRefId());
        assertTrue(largePayload.contains("\"text\""));
    }
}
