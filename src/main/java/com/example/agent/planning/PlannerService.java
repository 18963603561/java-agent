package com.example.agent.planning;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.governance.evaluation.CapabilityBoundaryEvaluator;
import com.example.agent.governance.evaluation.CapabilityEvaluationInput;
import com.example.agent.governance.evaluation.CapabilityEvaluationResult;
import com.example.agent.planning.builder.HeuristicPlanBuilder;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.context.PlanningContextMapper;
import com.example.agent.planning.engine.LlmPlanEngine;
import com.example.agent.planning.engine.LlmPlanEngineResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 规划服务。
 *
 * <p>用途：编排能力评估、LLM 规划与规则回退，输出可执行规划结果。
 * <p>边界：本类仅负责流程编排，不再承载模型调用、解析与规则构建细节。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final PlannerProperties plannerProperties;
    private final CapabilityBoundaryEvaluator capabilityBoundaryEvaluator;
    private final LlmPlanEngine llmPlanEngine;
    private final HeuristicPlanBuilder heuristicPlanBuilder;
    private final PlanningContextMapper planningContextMapper;

    /**
     * 构造规划服务。
     *
     * @param plannerProperties 规划配置
     * @param capabilityBoundaryEvaluator 能力边界评估器
     * @param llmPlanEngine LLM 规划引擎
     * @param heuristicPlanBuilder 规则规划构建器
     * @param planningContextMapper 规划上下文映射器
     */
    public PlannerService(PlannerProperties plannerProperties,
                          CapabilityBoundaryEvaluator capabilityBoundaryEvaluator,
                          LlmPlanEngine llmPlanEngine,
                          HeuristicPlanBuilder heuristicPlanBuilder,
                          PlanningContextMapper planningContextMapper) {
        this.plannerProperties = plannerProperties;
        this.capabilityBoundaryEvaluator = capabilityBoundaryEvaluator;
        this.llmPlanEngine = llmPlanEngine;
        this.heuristicPlanBuilder = heuristicPlanBuilder;
        this.planningContextMapper = planningContextMapper;
    }

    /**
     * 规划入口。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request, TenantContext tenantContext) {
        return plan(request, tenantContext, null, null);
    }

    /**
     * 带链路上下文的规划入口。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @return 规划结果
     */
    public PlanResult plan(TaskRequest request,
                           TenantContext tenantContext,
                           String workflowId,
                           AtomicLong seqCounter) {
        String planId = UUID.randomUUID().toString();
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        log.info("规划开始, tenantId={}, workflowId={}, planId={}, llmEnabled={}, fallbackEnabled={}",
                tenantId,
                workflowId,
                planId,
                plannerProperties.isLlmEnabled(),
                plannerProperties.isFallbackEnabled());
        PlanningContext planningContext = planningContextMapper.fromTaskRequest(request);

        CapabilityEvaluationResult evaluation = evaluateCapability(request, planningContext, tenantContext, workflowId,
                seqCounter);
        applyEvaluationToContext(planningContext, evaluation);

        if (plannerProperties.isLlmEnabled()) {
            LlmPlanEngineResult llmResult = llmPlanEngine.execute(request,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    planningContext,
                    planId);
            if (llmResult.isSuccess()) {
                PlanResult result = llmResult.getPlanResult();
                applyApprovalRequirement(result, request, evaluation);
                log.info("规划结束(LLM), tenantId={}, workflowId={}, planId={}, steps={}",
                        tenantId,
                        workflowId,
                        planId,
                        result != null && result.getSteps() != null ? result.getSteps().size() : 0);
                return result;
            }
            log.warn("LLM 规划未产出结果，进入规则回退, tenantId={}, workflowId={}, planId={}",
                    tenantId,
                    workflowId,
                    planId);
        }

        if (!plannerProperties.isFallbackEnabled()) {
            log.error("规划失败，回退关闭, tenantId={}, workflowId={}, planId={}", tenantId, workflowId, planId);
            throw new IllegalStateException("planner_fallback_disabled");
        }

        PlanResult fallback = heuristicPlanBuilder.build(planId,
                request != null ? request.getQuery() : null,
                planningContext,
                tenantId);
        applyApprovalRequirement(fallback, request, evaluation);
        log.info("规划结束(规则回退), tenantId={}, workflowId={}, planId={}, steps={}",
                tenantId,
                workflowId,
                planId,
                fallback != null && fallback.getSteps() != null ? fallback.getSteps().size() : 0);
        return fallback;
    }

    /**
     * 评估能力边界。
     *
     * @param request 任务请求
     * @param planningContext 规划上下文
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @return 评估结果
     */
    private CapabilityEvaluationResult evaluateCapability(TaskRequest request,
                                                          PlanningContext planningContext,
                                                          TenantContext tenantContext,
                                                          String workflowId,
                                                          AtomicLong seqCounter) {
        if (capabilityBoundaryEvaluator == null || !capabilityBoundaryEvaluator.isEnabled()) {
            return null;
        }
        CapabilityEvaluationInput input = new CapabilityEvaluationInput();
        input.setTaskDescription(request != null ? request.getQuery() : null);
        input.setPlanSummary(resolvePlanSummary(planningContext));
        input.setToolSummary(resolveToolSummary(planningContext));
        input.setBudgetThresholdTokens(resolveBudgetThreshold(planningContext));
        input.setFailureTypes(resolveFailureTypes(planningContext));
        input.setComplexityScore(heuristicPlanBuilder.estimateComplexity(request != null ? request.getQuery() : null));
        return capabilityBoundaryEvaluator.evaluate(input, tenantContext, workflowId, seqCounter);
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
            mapStrategyToContext(planningContext, evaluation.getRecommendedStrategy());
        }
        planningContext.put(PlanningContextKeys.CAPABILITY_SCORE, evaluation.getComplexityScore());
        planningContext.put(PlanningContextKeys.CAPABILITY_RISK,
                evaluation.getRiskLevel() != null ? evaluation.getRiskLevel().name() : null);
    }

    private void applyApprovalRequirement(PlanResult plan,
                                          TaskRequest request,
                                          CapabilityEvaluationResult evaluation) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        if (evaluation == null || evaluation.isSkipped() || !evaluation.isShouldAskApproval()) {
            return;
        }
        if (hasExplicitApproval(request) || hasExplicitApproval(plan.getSteps())) {
            return;
        }
        StepSpec first = plan.getSteps().get(0);
        markStepRequiresApproval(first, "evaluation");
    }

    private boolean hasExplicitApproval(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return false;
        }
        return request.getContext().containsKey(PlanningContextKeys.REQUIRES_APPROVAL);
    }

    private boolean hasExplicitApproval(List<StepSpec> steps) {
        if (steps == null) {
            return false;
        }
        for (StepSpec step : steps) {
            if (step != null && step.getRequiresApproval() != null) {
                return true;
            }
        }
        return false;
    }

    private void markStepRequiresApproval(StepSpec step, String source) {
        if (step == null) {
            return;
        }
        step.setRequiresApproval(true);
        if (source != null && !source.isBlank()) {
            step.setApprovalSource(source);
        }
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

    private void mapStrategyToContext(PlanningContext planningContext, String strategy) {
        if (planningContext == null || strategy == null) {
            return;
        }
        String normalized = strategy.toLowerCase(Locale.ROOT);
        if (normalized.contains("tree") || normalized.contains("tot")) {
            planningContext.put(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY, "tree_of_thoughts");
            return;
        }
        if (normalized.contains("debate")) {
            planningContext.put(PlanningContextKeys.STRATEGY, "debate");
            return;
        }
        if (normalized.contains("research")) {
            planningContext.put(PlanningContextKeys.MODE, "deep_research");
            planningContext.put(PlanningContextKeys.STRATEGY, "research");
            return;
        }
        if (normalized.contains("react")) {
            planningContext.put(PlanningContextKeys.REACT, true);
        }
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
