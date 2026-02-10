package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.reflection.model.ReflectionOutputDigest;
import com.example.agent.reflection.model.ReflectionOutputSummary;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicReflectionStrategyTest {

    @Test
    void requestsRetryWhenDigestAndSummaryIndicateLowQuality() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setConfidenceThreshold(0.85);
        properties.setMinOutputChars(60);
        properties.setMaxRetries(2);
        properties.setRequiredKeys(List.of("result", "data"));
        properties.setFailureKeywords(List.of("error", "failed"));
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);

        HeuristicReflectionStrategy strategy = new HeuristicReflectionStrategy(properties, metricsPublisher);

        ReflectionContext reflectionContext = ReflectionContext.builder()
                .stepType("TOOL")
                .attempt(1)
                .outputSummary(new ReflectionOutputSummary("failed: dependency timeout"))
                .outputDigest(new ReflectionOutputDigest(1, List.of("result"), 20, false))
                .build();

        ReflectionExecutionContext context = ReflectionExecutionContext.builder()
                .step(new StepSpec("TOOL", Map.of("critical", true)))
                .output(StepExecutionOutput.fromPayload(Map.of("result", "x")))
                .tenantContext(new TenantContext("t1", "u1", List.of(), "req", "trace"))
                .attempt(1)
                .workflowId("wf-1")
                .seqCounter(new AtomicLong(0))
                .reflectionContext(reflectionContext)
                .build();

        ReflectionDecision decision = strategy.execute(context);

        assertNotNull(decision);
        assertNotNull(decision.getResult());
        assertTrue(decision.getResult().retryRequested());
        assertNotNull(decision.getResult().report());
        assertTrue(decision.getResult().report().score() < 0.85);
    }

    @Test
    void keepsPassWhenSummaryAndDigestAreHealthy() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setConfidenceThreshold(0.7);
        properties.setMinOutputChars(20);
        properties.setMaxRetries(2);
        properties.setRequiredKeys(List.of("result"));
        properties.setFailureKeywords(List.of("error", "failed"));
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);

        HeuristicReflectionStrategy strategy = new HeuristicReflectionStrategy(properties, metricsPublisher);

        ReflectionContext reflectionContext = ReflectionContext.builder()
                .stepType("TOOL")
                .attempt(1)
                .outputSummary(new ReflectionOutputSummary("result is stable and validated"))
                .outputDigest(new ReflectionOutputDigest(2, List.of("result", "data"), 120, false))
                .build();

        ReflectionExecutionContext context = ReflectionExecutionContext.builder()
                .step(new StepSpec("TOOL", Map.of("critical", false)))
                .output(StepExecutionOutput.fromPayload(Map.of("result", "ok")))
                .tenantContext(new TenantContext("t1", "u1", List.of(), "req", "trace"))
                .attempt(1)
                .workflowId("wf-1")
                .seqCounter(new AtomicLong(0))
                .reflectionContext(reflectionContext)
                .build();

        ReflectionDecision decision = strategy.execute(context);

        assertNotNull(decision);
        assertNotNull(decision.getResult());
        assertFalse(decision.getResult().retryRequested());
        assertTrue(decision.getResult().report().score() >= 0.7);
    }
}
