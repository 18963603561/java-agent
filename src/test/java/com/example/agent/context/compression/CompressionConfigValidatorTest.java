package com.example.agent.context.compression;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.config.CompressionConfigValidationResult;
import com.example.agent.capabilities.context.compression.config.CompressionConfigValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩配置校验器测试。
 */
class CompressionConfigValidatorTest {

    @Test
    void shouldReturnInvalidWhenFallbackUnsupported() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getLlm().setFallback("unknown");

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("llm_fallback_invalid"));
    }

    @Test
    void shouldReturnValidWhenConfigurationCorrect() {
        ContextCompressionProperties properties = new ContextCompressionProperties();

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertTrue(result.isValid());
    }
}

