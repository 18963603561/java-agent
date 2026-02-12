package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
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

    @Test
    void shouldReturnInvalidWhenTriggerRatioLessThanTargetRatio() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getTrigger().setCompressionTriggerRatio(0.6D);
        properties.getTrigger().setCompressionTargetRatio(0.7D);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("trigger_target_ratio_relation_invalid"));
    }

    @Test
    void shouldReturnInvalidWhenInjectionRoleUnsupported() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getInjection().setRole("system");

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("injection_role_invalid"));
    }

    @Test
    void shouldReturnInvalidWhenRolloutVersionMissing() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setVersion(" ");

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("rollout_version_missing"));
    }

    @Test
    void shouldReturnInvalidWhenRolloutRatioOutOfRange() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setGlobalRatio(1.5D);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("rollout_global_ratio_invalid"));
    }
}

