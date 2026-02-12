package com.example.agent.context.compression;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.application.CompressionExecutionRouter;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.experiment.application.CompressionDualTrackOrchestrator;
import com.example.agent.capabilities.context.compression.experiment.application.guard.CompressionRollbackGuard;
import com.example.agent.capabilities.context.compression.experiment.domain.model.DualTrackExecutionResult;
import com.example.agent.capabilities.context.compression.experiment.infrastructure.evaluator.RuleBasedCompressionQualityEvaluator;
import com.example.agent.capabilities.context.compression.experiment.infrastructure.policy.DefaultCompressionRolloutPolicy;
import com.example.agent.capabilities.context.compression.experiment.infrastructure.repository.InMemoryCompressionComparisonRepository;
import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 双轨编排器测试。
 */
class CompressionDualTrackOrchestratorTest {

    @Test
    void shouldReturnPrimaryWhenDualTrackDisabled() {
        CompressionExecutionRouter router = Mockito.mock(CompressionExecutionRouter.class);
        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSource("rule");
        primary.setSuccess(true);
        when(router.resolveConfiguredMode()).thenReturn("rule");
        when(router.execute(any(CompressionCommand.class), eq("rule"))).thenReturn(primary);

        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(false);
        InMemoryCompressionComparisonRepository repository = new InMemoryCompressionComparisonRepository();
        CompressionDualTrackOrchestrator orchestrator = new CompressionDualTrackOrchestrator(
                router,
                new DefaultCompressionRolloutPolicy(properties),
                repository,
                new RuleBasedCompressionQualityEvaluator(),
                new CompressionRollbackGuard(properties));

        CompressionCommand command = new CompressionCommand();
        command.setRequest(new ContextCompressionRequest());
        DualTrackExecutionResult result = orchestrator.execute(command, "t1", "context_compress", "s1", "wf1");

        assertNotNull(result.getPrimaryResult());
        assertTrue(result.getPrimaryResult().isSuccess());
        assertFalse(result.isDualTrackEnabled());
        assertEquals(0, repository.findAll().size());
    }

    @Test
    void shouldPersistComparisonWhenDualTrackEnabled() {
        CompressionExecutionRouter router = Mockito.mock(CompressionExecutionRouter.class);
        CompressionExecutionResult primary = new CompressionExecutionResult();
        primary.setSource("rule");
        primary.setSuccess(true);
        CompressionExecutionResult shadow = new CompressionExecutionResult();
        shadow.setSource("llm");
        shadow.setSuccess(false);
        shadow.setFailureReason("LLM_FAILED");

        when(router.resolveConfiguredMode()).thenReturn("rule");
        when(router.execute(any(CompressionCommand.class), eq("rule"))).thenReturn(primary);
        when(router.execute(any(CompressionCommand.class), eq("llm"))).thenReturn(shadow);

        ContextCompressionProperties properties = new ContextCompressionProperties();
        properties.getRollout().setDualTrackEnabled(true);
        properties.getRollout().setGlobalRatio(1D);
        InMemoryCompressionComparisonRepository repository = new InMemoryCompressionComparisonRepository();
        CompressionDualTrackOrchestrator orchestrator = new CompressionDualTrackOrchestrator(
                router,
                new DefaultCompressionRolloutPolicy(properties),
                repository,
                new RuleBasedCompressionQualityEvaluator(),
                new CompressionRollbackGuard(properties));

        CompressionCommand command = new CompressionCommand();
        command.setRequest(new ContextCompressionRequest());
        DualTrackExecutionResult result = orchestrator.execute(command, "t2", "context_compress", "s2", "wf2");

        assertTrue(result.isDualTrackEnabled());
        assertNotNull(result.getShadowResult());
        assertEquals(1, repository.findAll().size());
        assertEquals("rule", repository.findAll().get(0).getPrimarySource());
        assertEquals("llm", repository.findAll().get(0).getShadowSource());
    }
}


