package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.FileRawResultStore;
import com.example.agent.runtime.raw.store.FileRawStorageSupport;
import com.example.agent.runtime.raw.store.InMemoryRawResultStore;
import com.example.agent.runtime.raw.store.RawResultStoreRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawResultStoreRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadByRefIdShouldRouteToMatchedStore() {
        RawRefCodec codec = new RawRefCodec();
        InMemoryRawResultStore memStore = new InMemoryRawResultStore(new ObjectMapper(), codec);

        RawStoreProperties properties = new RawStoreProperties();
        properties.setFileBaseDir(tempDir.toString());
        properties.setFilePublicBaseUrl("http://127.0.0.1:8080/api/v1/raw/files");
        FileRawResultStore fileStore = new FileRawResultStore(properties, codec, new FileRawStorageSupport());

        RawResultStoreRegistry registry = new RawResultStoreRegistry(List.of(memStore, fileStore), codec);

        RawRef memRef = memStore.store("tool:test", Map.of("k", 1), "application/json");
        String memPayload = registry.loadByRefId(memRef.getRefId());
        assertNotNull(memPayload);
        assertTrue(memPayload.contains("\"k\":1"));

        RawRef fileRef = fileStore.store("tool:file", "file-body", "text/plain");
        String filePayload = registry.loadByRefId(fileRef.getRefId());
        assertEquals("file-body", filePayload);
    }

    @Test
    void findRequiredShouldMatchTxtAliasToFileStore() {
        RawRefCodec codec = new RawRefCodec();
        RawStoreProperties properties = new RawStoreProperties();
        properties.setFileBaseDir(tempDir.toString());
        FileRawResultStore fileStore = new FileRawResultStore(properties, codec, new FileRawStorageSupport());
        RawResultStoreRegistry registry = new RawResultStoreRegistry(List.of(fileStore), codec);

        assertTrue(registry.supports(RawStoreType.FILE));
        assertTrue(registry.supports(RawStoreType.TXT));
        assertEquals(fileStore, registry.findRequired(RawStoreType.FILE));
        assertEquals(fileStore, registry.findRequired(RawStoreType.TXT));
    }
}

