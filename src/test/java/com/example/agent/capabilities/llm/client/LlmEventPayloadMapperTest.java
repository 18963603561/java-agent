package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.client.events.LlmEventPayloadMapper;
import com.example.agent.capabilities.llm.client.events.LlmParseEventPayload;
import com.example.agent.capabilities.llm.client.events.LlmPromptEventPayload;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LlmEventPayloadMapper 测试。
 */
class LlmEventPayloadMapperTest {

    @Test
    void toMapShouldProtectPromptReservedKeys() {
        LlmEventPayloadMapper mapper = new LlmEventPayloadMapper();
        LlmPromptEventPayload payload = new LlmPromptEventPayload(
                "plan",
                "CHEAP",
                "openai",
                "model-a",
                "wf-1",
                "trace-1",
                "success",
                null,
                "hello",
                1,
                List.of("USER"),
                null,
                Map.of("scene", "override", "customKey", "value"));

        Map<String, Object> mapped = mapper.toMap(payload);

        assertEquals("CHEAP", mapped.get("scene"));
        assertEquals("override", mapped.get("ext_scene"));
        assertEquals("value", mapped.get("customKey"));
    }

    @Test
    void toMapShouldProtectParseReservedKeys() {
        LlmEventPayloadMapper mapper = new LlmEventPayloadMapper();
        LlmParseEventPayload payload = new LlmParseEventPayload(
                "final",
                "CHEAP",
                "openai",
                "model-a",
                "wf-1",
                "trace-1",
                "failed",
                "raw-ref-1",
                "content",
                1,
                2,
                3,
                "MODEL_TIMEOUT",
                "timeout",
                "TimeoutException",
                true,
                null,
                Map.of("errorCode", "OVERRIDE", "note", "x"));

        Map<String, Object> mapped = mapper.toMap(payload);

        assertEquals("MODEL_TIMEOUT", mapped.get("errorCode"));
        assertEquals("OVERRIDE", mapped.get("ext_errorCode"));
        assertEquals("x", mapped.get("note"));
        assertFalse(mapped.containsKey("ext_note"));
        assertTrue(mapped.containsKey("retriable"));
    }
}
