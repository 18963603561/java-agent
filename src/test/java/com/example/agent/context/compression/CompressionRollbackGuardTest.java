package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.application.guard.CompressionRollbackGuard;
import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionQualityScore;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RollbackDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回滚守卫测试。
 */
class CompressionRollbackGuardTest {

    @Test
    void shouldRollbackWhenPrimaryFailedAndShadowSuccess() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        CompressionRollbackGuard guard = new CompressionRollbackGuard(properties);

        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSource("rule");
        primary.setSuccess(false);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSource("llm");
        shadow.setSuccess(true);

        RollbackDecision decision = guard.evaluate(primary, shadow, new CompressionQualityScore());

        assertTrue(decision.isRollback());
        assertEquals("PRIMARY_FAILED", decision.getReason());
        assertEquals("llm", decision.getWinnerSource());
    }

    @Test
    void shouldRollbackWhenQualityTooLow() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getTrigger().setCompressionTargetRatio(0.9D);
        CompressionRollbackGuard guard = new CompressionRollbackGuard(properties);

        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSource("rule");
        primary.setSuccess(true);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSource("llm");
        shadow.setSuccess(true);

        CompressionQualityScore score = new CompressionQualityScore();
        score.setScore(70D);
        score.setShadowPreferred(true);

        RollbackDecision decision = guard.evaluate(primary, shadow, score);

        assertTrue(decision.isRollback());
        assertEquals("QUALITY_SCORE_BELOW_THRESHOLD", decision.getReason());
        assertEquals("llm", decision.getWinnerSource());
    }

    @Test
    void shouldKeepPrimaryWhenNoRollbackSignal() {
        ContextCompressionProperties properties = new ContextCompressionProperties();
        CompressionRollbackGuard guard = new CompressionRollbackGuard(properties);

        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSource("rule");
        primary.setSuccess(true);
        primary.setDurationMs(10L);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSource("llm");
        shadow.setSuccess(true);

        CompressionQualityScore score = new CompressionQualityScore();
        score.setScore(95D);
        score.setShadowPreferred(false);

        RollbackDecision decision = guard.evaluate(primary, shadow, score);

        assertFalse(decision.isRollback());
        assertEquals("PRIMARY_KEPT", decision.getReason());
        assertEquals("rule", decision.getWinnerSource());
    }
}


