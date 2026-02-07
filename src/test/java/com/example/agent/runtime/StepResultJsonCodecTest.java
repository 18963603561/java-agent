package com.example.agent.runtime;

import com.example.agent.runtime.codec.StepResultJsonCodec;
import com.example.agent.runtime.model.StepResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 步骤结果 JSON 编解码器测试。
 */
class StepResultJsonCodecTest {

    @Test
    void readMapShouldReturnNullWhenJsonInvalid() {
        StepResultJsonCodec codec = new StepResultJsonCodec(new ObjectMapper());

        Map<String, Object> value = codec.readMap("{invalid_json");

        assertNull(value);
    }

    @Test
    void readStepResultShouldReturnNullWhenJsonInvalid() {
        StepResultJsonCodec codec = new StepResultJsonCodec(new ObjectMapper());

        StepResult result = codec.readStepResult("{invalid_json");

        assertNull(result);
    }

    @Test
    void writeShouldReturnNullWhenPayloadNotSerializable() {
        StepResultJsonCodec codec = new StepResultJsonCodec(new ObjectMapper());

        String json = codec.write(new SelfReferencePayload());

        assertNull(json);
    }

    @Test
    void writeAndReadShouldSucceedWhenJsonValid() {
        StepResultJsonCodec codec = new StepResultJsonCodec(new ObjectMapper());

        String json = codec.write(Map.of("key", "value"));
        Map<String, Object> parsed = codec.readMap(json);

        assertNotNull(json);
        assertNotNull(parsed);
        assertEquals("value", parsed.get("key"));
    }

    /**
     * 自引用对象，用于触发 Jackson 序列化异常。
     */
    private static final class SelfReferencePayload {
        private final SelfReferencePayload self = this;

        public SelfReferencePayload getSelf() {
            return self;
        }
    }
}
