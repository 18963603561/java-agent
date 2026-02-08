package com.example.agent.planning;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.governance.evaluation.CapabilityRiskLevel;
import com.example.agent.planning.approval.PlanningApprovalService;
import com.example.agent.planning.builder.HeuristicPlanBuilder;
import com.example.agent.planning.capability.PlanningCapabilityService;
import com.example.agent.planning.capability.PlanningRecommendationMapper;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.runtime.model.StepSpec;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 规划能力评估与审批服务测试。
 */
class PlanningCapabilityAndApprovalServiceTest {

    @Test
    void evaluateAndApplyShouldWriteEvaluationAndStrategy() {
        CapabilityBoundaryEvaluator evaluator = Mockito.mock(CapabilityBoundaryEvaluator.class);
        HeuristicPlanBuilder heuristicPlanBuilder = Mockito.mock(HeuristicPlanBuilder.class);
        when(evaluator.isEnabled()).thenReturn(true);
        when(heuristicPlanBuilder.estimateComplexity(anyString())).thenReturn(0.88);

        CapabilityEvaluationResult evaluation = new CapabilityEvaluationResult();
        evaluation.setSkipped(false);
        evaluation.setShouldAskApproval(true);
        evaluation.setComplexityScore(0.88);
        evaluation.setRiskLevel(CapabilityRiskLevel.HIGH);
        evaluation.setRecommendedStrategy("tot");
        when(evaluator.evaluate(any(CapabilityEvaluationInput.class), isNull(), isNull(), any(AtomicLong.class)))
                .thenReturn(evaluation);

        PlanningCapabilityService service = new PlanningCapabilityService(evaluator,
                heuristicPlanBuilder,
                new PlanningRecommendationMapper());
        PlanningContext context = new PlanningContext(new HashMap<>());

        TaskRequest request = new TaskRequest();
        request.setQuery("复杂问题");
        CapabilityEvaluationResult result = service.evaluateAndApply(request,
                context,
                null,
                null,
                new AtomicLong(0));

        assertNotNull(result);
        assertEquals(0.88, context.get(PlanningContextKeys.CAPABILITY_SCORE));
        assertEquals("HIGH", context.get(PlanningContextKeys.CAPABILITY_RISK));
        assertEquals(true, context.get(PlanningContextKeys.REQUIRES_APPROVAL));
        assertEquals(PlanningContextKeys.STRATEGY_TREE_OF_THOUGHTS,
                context.get(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY));
    }

    @Test
    void applyApprovalRequirementShouldMarkFirstStep() {
        PlanningApprovalService service = new PlanningApprovalService();
        StepSpec first = new StepSpec();
        first.setStepType("TOOL");
        PlanResult planResult = new PlanResult("plan-1", "summary", List.of(first));

        CapabilityEvaluationResult evaluation = new CapabilityEvaluationResult();
        evaluation.setSkipped(false);
        evaluation.setShouldAskApproval(true);

        TaskRequest request = new TaskRequest();
        request.setContext(new HashMap<>());
        service.applyApprovalRequirement(planResult, request, evaluation);

        assertTrue(Boolean.TRUE.equals(first.getRequiresApproval()));
        assertEquals("evaluation", first.getApprovalSource());
    }
}
