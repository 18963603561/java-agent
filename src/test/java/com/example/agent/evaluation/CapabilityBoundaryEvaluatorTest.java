package com.example.agent.evaluation;

import com.example.agent.auth.TenantContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityBoundaryEvaluatorTest {

    @Test
    void highRiskRaisesEventAndRecommendsStrategy() {
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();
        properties.setEnabled(true);
        properties.setRiskThreshold(0.6);
        properties.setComplexityThreshold(0.6);
        properties.setForceApprovalAboveRisk(true);

        TestEventPublisher publisher = new TestEventPublisher();
        CapabilityBoundaryEvaluator evaluator = new CapabilityBoundaryEvaluator(
                properties, publisher, Mockito.mock(com.example.agent.streaming.EventStreamService.class));

        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription("请对大型复杂系统进行深入调研并输出综合报告与证据来源，包含风险评估与对比分析");
        input.setToolSummary("");
        input.setBudgetThresholdTokens(1000);
        input.setFailureTypes(List.of("TOOL_ERROR"));
        input.setComplexityScore(0.9);

        CapabilityEvaluationResult result = evaluator.evaluate(
                input, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));

        assertEquals(CapabilityRiskLevel.HIGH, result.getRiskLevel());
        assertNotEquals("tool", result.getRecommendedStrategy());
        assertTrue(result.isShouldAskApproval());
        assertTrue(publisher.events.stream().anyMatch(event ->
                event.getType() == EventType.CAPABILITY_EVAL_RISK_RAISED));
    }

    static class TestEventPublisher implements ApplicationEventPublisher {
        private final List<StreamEvent> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                events.add(streamEvent);
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            // 不处理 ApplicationEvent 分支
        }
    }

}
