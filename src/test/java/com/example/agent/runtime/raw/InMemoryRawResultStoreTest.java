package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import com.example.agent.runtime.raw.store.InMemoryRawResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InMemoryRawResultStore 存储与读取测试。
 */
class InMemoryRawResultStoreTest {

    @Test
    void storeShouldBuildRawRefAndPersistText() {
        InMemoryRawResultStore store = new InMemoryRawResultStore(new ObjectMapper(), new RawRefCodec());

        RawRef ref = store.store("tool:query", Map.of("a", 1), "application/json");

        assertNotNull(ref);
        assertEquals("mem", ref.getStore());
        assertNotNull(ref.getKey());
        assertTrue(ref.getKey().startsWith("raw:tool:query:"));
        assertNotNull(ref.getRefId());
        assertTrue(ref.getRefId().startsWith("rawref:v1:mem:"));
        assertEquals("explicit_mem_mode", ref.getStoreReason());
        assertNotNull(ref.getHash());

        String text = store.load(ref.getKey());
        assertNotNull(text);
        assertTrue(text.contains("\"a\":1"));

        String textByRef = store.loadByRefId(ref.getRefId());
        assertEquals(text, textByRef);

        String textByStoreId = store.loadByStoreId(ref.getKey());
        assertEquals(text, textByStoreId);
        assertEquals(RawStoreType.MEM, store.storeType());
    }
}
