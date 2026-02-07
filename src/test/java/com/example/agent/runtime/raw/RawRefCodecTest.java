package com.example.agent.runtime.raw;

import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RawRefCodec 编解码测试。
 */
class RawRefCodecTest {

    @Test
    void encodeAndParseShouldRoundTrip() {
        RawRefCodec codec = new RawRefCodec();

        String refId = codec.encode(RawStoreType.REDIS, "raw:model:planner:1");
        ParsedRawRef parsed = codec.parse(refId);

        assertEquals("rawref:v1:redis:raw:model:planner:1", refId);
        assertEquals("v1", parsed.version());
        assertEquals(RawStoreType.REDIS, parsed.storeType());
        assertEquals("raw:model:planner:1", parsed.id());
    }

    @Test
    void isRawRefShouldMatchPrefix() {
        RawRefCodec codec = new RawRefCodec();

        assertTrue(codec.isRawRef("rawref:v1:mem:raw:1"));
        assertFalse(codec.isRawRef("raw:model:planner:1"));
        assertFalse(codec.isRawRef(""));
    }

    @Test
    void parseShouldRejectInvalidPattern() {
        RawRefCodec codec = new RawRefCodec();

        assertThrows(IllegalArgumentException.class, () -> codec.parse("rawref:v2:mem:1"));
        assertThrows(IllegalArgumentException.class, () -> codec.parse("bad:v1:mem:1"));
        assertThrows(IllegalArgumentException.class, () -> codec.parse("rawref:v1:unknown:1"));
    }
}

