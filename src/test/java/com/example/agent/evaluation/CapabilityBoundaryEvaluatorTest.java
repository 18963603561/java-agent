package com.example.agent.evaluation;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.governance.evaluation.CapabilityRiskLevel;
import com.example.agent.governance.evaluation.domain.BudgetPressureRiskRule;
import com.example.agent.governance.evaluation.domain.CapabilityRuleRegistry;
import com.example.agent.governance.evaluation.domain.ComplexityThresholdRiskRule;
import com.example.agent.governance.evaluation.domain.DebateKeywordStrategyRule;
import com.example.agent.governance.evaluation.domain.FailureTypesRiskRule;
import com.example.agent.governance.evaluation.domain.HighRiskThoughtTreeStrategyRule;
import com.example.agent.governance.evaluation.domain.MissingToolSummaryRiskRule;
import com.example.agent.governance.evaluation.domain.ResearchKeywordStrategyRule;

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
        CapabilityRuleRegistry capabilityRuleRegistry = buildRuleRegistry();
        CapabilityBoundaryEvaluator evaluator = new CapabilityBoundaryEvaluator(
                properties,
                publisher,
                Mockito.mock(com.example.agent.streaming.sse.EventStreamService.class),
                capabilityRuleRegistry);

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
        assertTrue(result.getRuleHits() != null && !result.getRuleHits().isEmpty());
        assertTrue(publisher.events.stream().anyMatch(event ->
                event.getType() == EventType.CAPABILITY_EVAL_RISK_RAISED));
    }

    @Test
    void disabledRuleShouldNotBeHitByEvaluator() {
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();
        properties.setEnabled(true);
        properties.setRiskThreshold(0.6);
        CapabilityEvaluationProperties.RuleSelection riskRules = new CapabilityEvaluationProperties.RuleSelection();
        riskRules.setBlacklist(List.of("missing_tool_summary"));
        properties.setRiskRules(riskRules);

        TestEventPublisher publisher = new TestEventPublisher();
        CapabilityRuleRegistry capabilityRuleRegistry = buildRuleRegistry();
        CapabilityBoundaryEvaluator evaluator = new CapabilityBoundaryEvaluator(
                properties,
                publisher,
                Mockito.mock(com.example.agent.streaming.sse.EventStreamService.class),
                capabilityRuleRegistry);

        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription("普通任务");
        input.setToolSummary("");
        input.setComplexityScore(0.1);

        CapabilityEvaluationResult result = evaluator.evaluate(
                input, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));

        assertTrue(result.getRuleHits().stream().noneMatch(hit -> "missing_tool_summary".equals(hit.getRuleId())));
    }

    @Test
    void sceneShouldUseExplicitSceneFieldInsteadOfPlanSummary() {
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();
        properties.setEnabled(true);
        CapabilityEvaluationProperties.RuleToggle sceneToggle = new CapabilityEvaluationProperties.RuleToggle();
        CapabilityEvaluationProperties.RuleSelection sceneStrategySelection = new CapabilityEvaluationProperties.RuleSelection();
        sceneStrategySelection.setWhitelist(List.of("debate_keyword"));
        sceneToggle.setStrategyRules(sceneStrategySelection);
        properties.setScenes(java.util.Map.of("analysis", sceneToggle));

        TestEventPublisher publisher = new TestEventPublisher();
        CapabilityRuleRegistry capabilityRuleRegistry = buildRuleRegistry();
        CapabilityBoundaryEvaluator evaluator = new CapabilityBoundaryEvaluator(
                properties,
                publisher,
                Mockito.mock(com.example.agent.streaming.sse.EventStreamService.class),
                capabilityRuleRegistry);

        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription("请做观点对比与辩论分析");
        input.setPlanSummary("不是场景键");
        input.setScene("analysis");
        input.setComplexityScore(0.3);

        CapabilityEvaluationResult result = evaluator.evaluate(
                input, new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));

        assertEquals("debate", result.getRecommendedStrategy());
    }

    private CapabilityRuleRegistry buildRuleRegistry() {
        return new CapabilityRuleRegistry(
                List.of(
                        new ComplexityThresholdRiskRule(),
                        new MissingToolSummaryRiskRule(),
                        new FailureTypesRiskRule(),
                        new BudgetPressureRiskRule()),
                List.of(
                        new ResearchKeywordStrategyRule(),
                        new DebateKeywordStrategyRule(),
                        new HighRiskThoughtTreeStrategyRule()));
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
