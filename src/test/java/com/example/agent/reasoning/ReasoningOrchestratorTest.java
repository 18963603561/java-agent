package com.example.agent.reasoning;

import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.reasoning.common.ReasoningResult;
import com.example.agent.reasoning.common.ReasoningStrategy;
import com.example.agent.reasoning.common.ReasoningStrategyRegistry;
import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.config.ReasoningConfigValidator;
import com.example.agent.reasoning.common.config.ReasoningExecutionProperties;
import com.example.agent.reasoning.common.orchestrator.ReasoningArbitrator;
import com.example.agent.reasoning.common.orchestrator.ReasoningExecutionPlan;
import com.example.agent.reasoning.common.orchestrator.ReasoningOrchestrator;
import com.example.agent.reasoning.common.result.CotPayload;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.telemetry.ReasoningMetricsPublisher;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReasoningOrchestratorTest {

    @Test
    void executeShouldFallbackWhenPrimaryFails() {
        ReasoningOrchestrator orchestrator = newOrchestrator(List.of(
                new FailingStrategy("cot"),
                new SuccessStrategy("debate", 0.8, "debate-result"),
                new SuccessStrategy("thought_tree", 0.7, "tree-result")
        ));

        ReasoningExecutionPlan plan = ReasoningExecutionPlan.builder()
                .parallelEnabled(false)
                .primaryStrategy("cot")
                .build();

        ReasoningResult result = orchestrator.execute(newRequest("cot"), plan);
        assertEquals("debate", result.getStrategyType());
        assertEquals("debate-result", result.getSummary());
    }

    @Test
    void executeShouldThrowWhenAllStrategiesFail() {
        ReasoningOrchestrator orchestrator = newOrchestrator(List.of(
                new FailingStrategy("cot"),
                new FailingStrategy("debate"),
                new FailingStrategy("thought_tree")
        ));

        ReasoningExecutionPlan plan = ReasoningExecutionPlan.builder()
                .parallelEnabled(false)
                .primaryStrategy("cot")
                .build();

        assertThrows(IllegalStateException.class, () -> orchestrator.execute(newRequest("cot"), plan));
    }

    @Test
    void executeShouldArbitrateParallelResults() {
        ReasoningOrchestrator orchestrator = newOrchestrator(List.of(
                new SuccessStrategy("cot", 0.5, "cot-result"),
                new SuccessStrategy("debate", 0.9, "debate-result")
        ));

        ReasoningExecutionPlan plan = ReasoningExecutionPlan.builder()
                .parallelEnabled(true)
                .primaryStrategy("cot")
                .candidateStrategies(List.of("cot", "debate"))
                .timeoutMillis(3000L)
                .build();

        ReasoningResult result = orchestrator.execute(newRequest("cot"), plan);
        assertEquals("debate", result.getStrategyType());
        assertEquals("debate-result", result.getSummary());
    }

    private ReasoningOrchestrator newOrchestrator(List<ReasoningStrategy> strategies) {
        ReasoningStrategyRegistry registry = new ReasoningStrategyRegistry(strategies);
        ReasoningExecutionProperties properties = new ReasoningExecutionProperties();
        ReasoningConfigResolver configResolver = new ReasoningConfigResolver(properties, new ReasoningConfigValidator());
        ReasoningMetricsPublisher metricsPublisher = new ReasoningMetricsPublisher(Mockito.mock(MetricsPublisher.class));
        Executor directExecutor = Runnable::run;
        return new ReasoningOrchestrator(
                registry,
                new ReasoningArbitrator(),
                directExecutor,
                metricsPublisher,
                configResolver,
                new ReasoningDegradePolicy(configResolver)
        );
    }

    private ReasoningRequest newRequest(String strategyType) {
        return new ReasoningRequest(strategyType, "prompt", Map.of(), null, "wf-1", new AtomicLong(0));
    }

    private static class SuccessStrategy implements ReasoningStrategy {
        private final String type;
        private final double confidence;
        private final String summary;

        private SuccessStrategy(String type, double confidence, String summary) {
            this.type = type;
            this.confidence = confidence;
            this.summary = summary;
        }

        @Override
        public boolean supports(String strategyType) {
            return type.equalsIgnoreCase(strategyType);
        }

        @Override
        public ReasoningResult execute(ReasoningRequest request) {
            return new ReasoningResult(type, summary, confidence, "completed", "COMPLETED", null,
                    new CotPayload(1, summary));
        }
    }

    private static class FailingStrategy implements ReasoningStrategy {
        private final String type;

        private FailingStrategy(String type) {
            this.type = type;
        }

        @Override
        public boolean supports(String strategyType) {
            return type.equalsIgnoreCase(strategyType);
        }

        @Override
        public ReasoningResult execute(ReasoningRequest request) {
            throw new IllegalStateException("failed:" + type);
        }
    }
}
