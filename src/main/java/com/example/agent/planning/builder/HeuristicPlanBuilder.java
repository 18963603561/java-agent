package com.example.agent.planning.builder;

import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.strategy.PlanningStrategyContext;
import com.example.agent.planning.strategy.PlanningStrategyRegistry;
import com.example.agent.planning.strategy.PlanningStrategyResult;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规则规划构建器。
 *
 * <p>用途：负责非 LLM 场景下的启发式步骤规划与策略分支生成。
 */
@Component
public class HeuristicPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(HeuristicPlanBuilder.class);

    private final PlanningStrategyRegistry strategyRegistry;

    /**
     * 构造规则规划构建器。
     *
     * @param strategyRegistry 策略注册中心
     */
    public HeuristicPlanBuilder(PlanningStrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * 生成启发式规划。
     *
     * @param planId 规划标识
     * @param query 查询文本
     * @param planningContext 规划上下文
     * @param tenantId 租户标识
     * @return 规划结果
     */
    public PlanResult build(String planId,
                            String query,
                            PlanningContext planningContext,
                            String tenantId) {
        if (planningContext == null) {
            planningContext = new PlanningContext(new java.util.HashMap<>());
        }
        double complexityScore = estimateComplexity(query);
        String cognitiveStrategy = resolveCognitiveStrategy(planningContext, complexityScore);
        String executionStrategy = resolveExecutionStrategy(planningContext);
        String mode = safeLowercase(planningContext.getString(PlanningContextKeys.MODE));
        String strategy = safeLowercase(planningContext.getString(PlanningContextKeys.STRATEGY));

        List<StepSpec> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        PlanningStrategyContext strategyContext = new PlanningStrategyContext(planId,
                tenantId,
                query,
                planningContext,
                complexityScore,
                cognitiveStrategy,
                executionStrategy,
                mode,
                strategy,
                steps,
                planSteps,
                dependencies,
                null);
        log.info("规则规划调度开始, tenantId={}, planId={}, strategy={}, mode={}, cognitive={}",
                tenantId,
                planId,
                strategy,
                mode,
                cognitiveStrategy);
        PlanningStrategyResult strategyResult = strategyRegistry.execute(strategyContext);
        List<StepSpec> resolvedSteps = resolveSteps(strategyResult, steps);
        List<Map<String, Object>> resolvedPlanSteps = resolvePlanSteps(strategyResult, planSteps);
        List<Map<String, String>> resolvedDependencies = resolveDependencies(strategyResult, dependencies);
        String scene = strategyResult != null && strategyResult.getScene() != null
                ? strategyResult.getScene()
                : "规则回退";
        return finalizePlan(planId,
                tenantId,
                scene,
                planningContext,
                executionStrategy,
                cognitiveStrategy,
                complexityScore,
                resolvedSteps,
                resolvedPlanSteps,
                resolvedDependencies);
    }

    /**
     * 估算复杂度。
     *
     * @param query 查询文本
     * @return 复杂度分值
     */
    public double estimateComplexity(String query) {
        if (query == null || query.isBlank()) {
            return 0.1;
        }
        String trimmed = query.trim();
        int length = trimmed.length();
        int clauses = trimmed.split("[，。！？!?\\s]+").length;
        double lengthScore = Math.min(1.0, length / 200.0);
        double clauseScore = Math.min(0.5, clauses * 0.1);
        return Math.min(1.0, lengthScore + clauseScore);
    }

    private PlanResult finalizePlan(String planId,
                                    String tenantId,
                                    String scene,
                                    PlanningContext planningContext,
                                    String executionStrategy,
                                    String cognitiveStrategy,
                                    double complexityScore,
                                    List<StepSpec> steps,
                                    List<Map<String, Object>> planSteps,
                                    List<Map<String, String>> dependencies) {
        String summary = String.format(Locale.ROOT,
                "strategy=%s, cognitive=%s, complexity=%.2f, steps=%d",
                executionStrategy,
                cognitiveStrategy,
                complexityScore,
                steps.size());
        planningContext.put(PlanningContextKeys.PLAN_STEPS, planSteps);
        planningContext.put(PlanningContextKeys.PLAN_DEPENDENCIES, dependencies);
        planningContext.put(PlanningContextKeys.EXECUTION_STRATEGY, executionStrategy);
        planningContext.put(PlanningContextKeys.COGNITIVE_STRATEGY, cognitiveStrategy);
        log.info("规划生成（{}）, tenantId={}, planId={}, summary={}", scene, tenantId, planId, summary);
        return PlanResult.readonly(planId, summary, steps);
    }

    private String resolveCognitiveStrategy(PlanningContext planningContext, double complexityScore) {
        String strategy = planningContext.getLowercaseString(PlanningContextKeys.STRATEGY);
        if (strategy == null) {
            strategy = planningContext.getLowercaseString(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY);
        }
        if (strategy != null) {
            return strategy;
        }
        if (complexityScore >= 0.7) {
            return PlanningContextKeys.STRATEGY_TREE_OF_THOUGHTS;
        }
        if (complexityScore >= 0.4) {
            return PlanningContextKeys.STRATEGY_REFLECTION;
        }
        return PlanningContextKeys.STRATEGY_SIMPLE;
    }

    private String resolveExecutionStrategy(PlanningContext planningContext) {
        String strategy = planningContext.getLowercaseString(PlanningContextKeys.EXECUTION_STRATEGY);
        if (strategy != null) {
            return strategy;
        }
        return PlanningContextKeys.EXECUTION_SEQUENTIAL;
    }

    private String safeLowercase(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private List<StepSpec> resolveSteps(PlanningStrategyResult strategyResult, List<StepSpec> fallback) {
        if (strategyResult != null && strategyResult.getSteps() != null && !strategyResult.getSteps().isEmpty()) {
            return new ArrayList<>(strategyResult.getSteps());
        }
        return fallback;
    }

    private List<Map<String, Object>> resolvePlanSteps(PlanningStrategyResult strategyResult,
                                                       List<Map<String, Object>> fallback) {
        if (strategyResult != null && strategyResult.getPlanSteps() != null && !strategyResult.getPlanSteps().isEmpty()) {
            return new ArrayList<>(strategyResult.getPlanSteps());
        }
        return fallback;
    }

    private List<Map<String, String>> resolveDependencies(PlanningStrategyResult strategyResult,
                                                          List<Map<String, String>> fallback) {
        if (strategyResult != null
                && strategyResult.getDependencies() != null
                && !strategyResult.getDependencies().isEmpty()) {
            return new ArrayList<>(strategyResult.getDependencies());
        }
        return fallback;
    }
}
