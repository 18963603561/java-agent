package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;
import com.example.agent.capabilities.context.compression.experiment.infrastructure.evaluator.RuleBasedCompressionQualityEvaluator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则质量评估器测试。
 */
class RuleBasedCompressionQualityEvaluatorTest {

    @Test
    void shouldPreferShadowWhenPrimaryFailed() {
        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSuccess(false);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSuccess(true);

        RuleBasedCompressionQualityEvaluator evaluator = new RuleBasedCompressionQualityEvaluator();
        CompressionQualityScore score = evaluator.evaluate(primary, shadow);

        assertTrue(score.isShadowPreferred());
        assertEquals("PRIMARY_FAILED_SHADOW_SUCCESS", score.getReason());
    }

    @Test
    void shouldMarkDegradationWhenPrimaryCompressionWeaker() {
        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSuccess(true);
        primary.setOriginalTokens(100);
        primary.setCompressedTokens(80);

        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSuccess(true);
        shadow.setOriginalTokens(100);
        shadow.setCompressedTokens(50);

        RuleBasedCompressionQualityEvaluator evaluator = new RuleBasedCompressionQualityEvaluator();
        CompressionQualityScore score = evaluator.evaluate(primary, shadow);

        assertTrue(score.getDegradationRate() > 0D);
        assertTrue(score.isShadowPreferred());
        assertEquals("PRIMARY_DEGRADATION_HIGH", score.getReason());
    }

    @Test
    void shouldKeepPrimaryWhenShadowFailed() {
        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSuccess(true);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSuccess(false);

        RuleBasedCompressionQualityEvaluator evaluator = new RuleBasedCompressionQualityEvaluator();
        CompressionQualityScore score = evaluator.evaluate(primary, shadow);

        assertFalse(score.isShadowPreferred());
        assertEquals("SHADOW_FAILED_PRIMARY_SUCCESS", score.getReason());
    }
}

