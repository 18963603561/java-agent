package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.parser.CompressionParseResult;
import com.example.agent.capabilities.context.compression.parser.CompressionResponseParser;
import com.example.agent.capabilities.context.compression.parser.DefaultCompressionValidationPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩响应解析器测试。
 */
class CompressionResponseParserTest {

    @Test
    void shouldParseValidJsonResponse() {
        CompressionResponseParser parser = new CompressionResponseParser(
                new ObjectMapper(),
                new DefaultCompressionValidationPolicy());

        CompressionParseResult result = parser.parse("{\"summary\":\"压缩摘要\",\"summaryVersion\":\"v2\"}");

        assertTrue(result.isSuccess());
        assertEquals("压缩摘要", result.getSummary());
        assertEquals("v2", result.getSummaryVersion());
    }

    @Test
    void shouldReturnParseErrorWhenJsonInvalid() {
        CompressionResponseParser parser = new CompressionResponseParser(
                new ObjectMapper(),
                new DefaultCompressionValidationPolicy());

        CompressionParseResult result = parser.parse("not-json");

        assertFalse(result.isSuccess());
        assertEquals("LLM_PARSE_ERROR", result.getFailureReason());
    }
}

