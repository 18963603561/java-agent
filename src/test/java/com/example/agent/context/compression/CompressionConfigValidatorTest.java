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

    @Test
    void shouldReturnInvalidWhenRollbackWindowNotPositive() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollback().setTriggerWindowMinutes(0);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("rollback_trigger_window_minutes_invalid"));
    }

    @Test
    void shouldReturnInvalidWhenRolloutRatioOutOfRange() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setGlobalRatio(1.2D);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("rollout_global_ratio_invalid"));
    }

    @Test
    void shouldReturnInvalidWhenBaselineRatioRelationIncorrect() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getBaseline().setDefaultTriggerRatio(0.5D);
        properties.getBaseline().setDefaultTargetRatio(0.7D);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("baseline_ratio_relation_invalid"));
    }

    @Test
    void shouldReturnWarningWhenEmergencyDisablesCompressionButTriggerEnabled() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getEmergency().setDisableCompression(true);
        properties.getTrigger().setEnabled(true);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertTrue(result.isValid());
        assertTrue(result.getWarnings().contains("emergency_disable_overrides_trigger"));
    }

    @Test
    void shouldReturnInvalidWhenQualityGateParseFailureRateOutOfRange() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getQualityGate().setMaxParseFailureRate(-0.1D);

        CompressionConfigValidator validator = new CompressionConfigValidator(properties);
        CompressionConfigValidationResult result = validator.validate();

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("quality_gate_parse_failure_rate_invalid"));
    }
}
