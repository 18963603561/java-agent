package com.example.agent.planning.capability;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.builder.HeuristicPlanBuilder;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划能力评估服务。
 *
 * <p>用途：负责能力评估输入组装、评估执行与结果回写，避免编排层承载规则细节。
 */
@Component
public class PlanningCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(PlanningCapabilityService.class);

    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    private final HeuristicPlanBuilder heuristicPlanBuilder;
    private final PlanningRecommendationMapper planningRecommendationMapper;

    /**
     * 构造能力评估服务。
     *
     * @param capabilityBoundaryEvaluator 能力边界评估器
     * @param heuristicPlanBuilder 规则规划构建器
     * @param planningRecommendationMapper 推荐策略映射器
     */
    public PlanningCapabilityService(CapabilityBoundaryEvaluator capabilityBoundaryEvaluator,
                                     HeuristicPlanBuilder heuristicPlanBuilder,
                                     PlanningRecommendationMapper planningRecommendationMapper) {
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.heuristicPlanBuilder = heuristicPlanBuilder;
        this.planningRecommendationMapper = planningRecommendationMapper;
    }

    /**
     * 评估能力边界并将结果回写到上下文。
     *
     * @param request 任务请求
     * @param planningContext 规划上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @return 评估结果，无需评估时返回 {@code null}
     */
    public CapabilityEvaluationResult evaluateAndApply(TaskRequest request,
                                                       PlanningContext planningContext,
                                                       TenantContext tenantContext,
                                                       String workflowId,
                                                       AtomicLong seqCounter) {
        if (capabilityBoundaryEvaluator == null || !capabilityBoundaryEvaluator.isEnabled()) {
            return null;
        }
        CapabilityEvaluationInput input = buildInput(request, planningContext);
        log.info("能力评估开始, tenantId={}, workflowId={}, complexityScore={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                input.getComplexityScore());
        CapabilityEvaluationResult evaluation = capabilityBoundaryEvaluator.evaluate(input,
                tenantContext,
                workflowId,
                seqCounter);
        applyEvaluationToContext(planningContext, evaluation);
        log.info("能力评估结束, tenantId={}, workflowId={}, skipped={}, shouldAskApproval={}, strategy={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                evaluation != null && evaluation.isSkipped(),
                evaluation != null && evaluation.isShouldAskApproval(),
                evaluation != null ? evaluation.getRecommendedStrategy() : null);
        return evaluation;
    }

    private CapabilityEvaluationInput buildInput(TaskRequest request, PlanningContext planningContext) {
        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription(request != null ? request.getQuery() : null);
        input.setPlanSummary(resolvePlanSummary(planningContext));
        input.setToolSummary(resolveToolSummary(planningContext));
        input.setBudgetThresholdTokens(resolveBudgetThreshold(planningContext));
        input.setFailureTypes(resolveFailureTypes(planningContext));
        input.setComplexityScore(heuristicPlanBuilder.estimateComplexity(request != null ? request.getQuery() : null));
        return input;
    }

    private void applyEvaluationToContext(PlanningContext planningContext, CapabilityEvaluationResult evaluation) {
        if (planningContext == null || evaluation == null || evaluation.isSkipped()) {
            return;
        }
        if (evaluation.isShouldAskApproval() && !planningContext.containsKey(PlanningContextKeys.REQUIRES_APPROVAL)) {
            planningContext.put(PlanningContextKeys.REQUIRES_APPROVAL, true);
            planningContext.putIfAbsent(PlanningContextKeys.APPROVAL_SOURCE, "evaluation");
        }
        if (!hasExplicitStrategy(planningContext) && evaluation.getRecommendedStrategy() != null) {
            planningRecommendationMapper.mapStrategyToContext(planningContext, evaluation.getRecommendedStrategy());
        }
        planningContext.put(PlanningContextKeys.CAPABILITY_SCORE, evaluation.getComplexityScore());
        planningContext.put(PlanningContextKeys.CAPABILITY_RISK,
                evaluation.getRiskLevel() != null ? evaluation.getRiskLevel().name() : null);
    }

    private boolean hasExplicitStrategy(PlanningContext planningContext) {
        if (planningContext == null) {
            return false;
        }
        return planningContext.containsKey(PlanningContextKeys.STRATEGY)
                || planningContext.containsKey(PlanningContextKeys.MODE)
                || planningContext.containsKey(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY)
                || planningContext.containsKey(PlanningContextKeys.REACT)
                || planningContext.containsKey(PlanningContextKeys.REACT_ENABLED);
    }

    private String resolvePlanSummary(PlanningContext planningContext) {
        if (planningContext == null) {
            return "";
        }
        String summary = planningContext.getString(PlanningContextKeys.PLAN_SUMMARY);
        return summary != null ? summary : "";
    }

    private String resolveToolSummary(PlanningContext planningContext) {
        if (planningContext == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendWithComma(builder, planningContext.getString(PlanningContextKeys.TOOL));
        appendWithComma(builder, planningContext.getString(PlanningContextKeys.TOOL_NAME));
        appendWithComma(builder, planningContext.getString(PlanningContextKeys.FALLBACK_TOOL));
        return builder.toString();
    }

    private void appendWithComma(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(value);
    }

    private int resolveBudgetThreshold(PlanningContext planningContext) {
        if (planningContext == null) {
            return 0;
        }
        Integer threshold = planningContext.getInteger(PlanningContextKeys.BUDGET_THRESHOLD_TOKENS);
        return threshold != null ? threshold : 0;
    }

    private List<String> resolveFailureTypes(PlanningContext planningContext) {
        if (planningContext == null) {
            return List.of();
        }
        return planningContext.getStringList(PlanningContextKeys.FAILURE_TYPES);
    }
}

