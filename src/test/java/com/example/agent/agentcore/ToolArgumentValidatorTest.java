package com.example.agent.agentcore;

import com.example.agent.common.error.ErrorCodeException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.tools.validation.ToolArgumentValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolArgumentValidatorTest {

    @Test
    void throwsWhenRequiredMissing() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", List.of("count"),
                "properties", Map.of("count", Map.of("type", "integer"))
        );
        Map<String, Object> arguments = Map.of("name", "demo");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> validator.validateAndNormalize(schema, arguments, "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
    }

    @Test
    void throwsWhenTypeMismatchAndNotCorrectable() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", List.of("count"),
                "properties", Map.of("count", Map.of("type", "integer"))
        );
        Map<String, Object> arguments = Map.of("count", "abc");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> validator.validateAndNormalize(schema, arguments, "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
    }

    @Test
    void convertsStringIntegerValue() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "required", List.of("count"),
                "properties", Map.of("count", Map.of("type", "integer"))
        );
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", "123");

        Map<String, Object> normalized = validator.validateAndNormalize(schema, arguments, "demo_tool");

        assertEquals(123L, normalized.get("count"));
    }

    @Test
    void throwsWhenAdditionalPropertiesDisallowed() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "additionalProperties", false
        );
        Map<String, Object> arguments = Map.of("name", "ok", "extra", "x");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> validator.validateAndNormalize(schema, arguments, "demo_tool"));

        assertEquals("INVALID_REQUEST", ex.getErrorCode());
    }
}
