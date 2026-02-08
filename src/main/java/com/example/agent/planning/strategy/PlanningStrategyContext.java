package com.example.agent.planning.strategy;

import com.example.agent.planning.context.PlanningContext;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 策略执行上下文。
 */
public class PlanningStrategyContext {

    private final String planId;
    private final String tenantId;
    private final String query;
    private final PlanningContext planningContext;
    private final double complexityScore;
    private final String cognitiveStrategy;
    private final String executionStrategy;
    private final String mode;
    private final String strategy;
    private final List<StepSpec> steps;
    private final List<Map<String, Object>> planSteps;
    private final List<Map<String, String>> dependencies;
    private final String previousStepKey;

    /**
     * 构造策略上下文。
     *
     * @param planId 计划标识
     * @param tenantId 租户标识
     * @param query 查询文本
     * @param planningContext 规划上下文
     * @param complexityScore 复杂度
     * @param cognitiveStrategy 认知策略
     * @param executionStrategy 执行策略
     * @param mode 模式
     * @param strategy 策略
     * @param steps 步骤
     * @param planSteps 计划步骤
     * @param dependencies 依赖
     * @param previousStepKey 前序步骤键
     */
    public PlanningStrategyContext(String planId,
                                   String tenantId,
                                   String query,
                                   PlanningContext planningContext,
                                   double complexityScore,
                                   String cognitiveStrategy,
                                   String executionStrategy,
                                   String mode,
                                   String strategy,
                                   List<StepSpec> steps,
                                   List<Map<String, Object>> planSteps,
                                   List<Map<String, String>> dependencies,
                                   String previousStepKey) {
        this(planId,
                tenantId,
                query,
                planningContext,
                complexityScore,
                cognitiveStrategy,
                executionStrategy,
                mode,
                strategy,
                steps != null ? new ArrayList<>(steps) : new ArrayList<>(),
                planSteps != null ? new ArrayList<>(planSteps) : new ArrayList<>(),
                dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>(),
                previousStepKey,
                true);
    }

    private PlanningStrategyContext(String planId,
                                   String tenantId,
                                   String query,
                                   PlanningContext planningContext,
                                   double complexityScore,
                                   String cognitiveStrategy,
                                   String executionStrategy,
                                   String mode,
                                   String strategy,
                                   List<StepSpec> steps,
                                   List<Map<String, Object>> planSteps,
                                   List<Map<String, String>> dependencies,
                                   String previousStepKey,
                                   boolean trustedInternalLists) {
        this.planId = planId;
        this.tenantId = tenantId;
        this.query = query;
        this.planningContext = planningContext;
        this.complexityScore = complexityScore;
        this.cognitiveStrategy = cognitiveStrategy;
        this.executionStrategy = executionStrategy;
        this.mode = mode;
        this.strategy = strategy;
        if (trustedInternalLists) {
            this.steps = steps;
            this.planSteps = planSteps;
            this.dependencies = dependencies;
        } else {
            this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
            this.planSteps = planSteps != null ? new ArrayList<>(planSteps) : new ArrayList<>();
            this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        }
        this.previousStepKey = previousStepKey;
    }

    public String getPlanId() {
        return planId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getQuery() {
        return query;
    }

    public PlanningContext getPlanningContext() {
        return planningContext;
    }

    public double getComplexityScore() {
        return complexityScore;
    }

    public String getCognitiveStrategy() {
        return cognitiveStrategy;
    }

    public String getExecutionStrategy() {
        return executionStrategy;
    }

    public String getMode() {
        return mode;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<StepSpec> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public List<Map<String, Object>> getPlanSteps() {
        return Collections.unmodifiableList(planSteps);
    }

    public List<Map<String, String>> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    /**
     * 返回当前步骤数量。
     *
     * @return 步骤数量
     */
    public int getStepCount() {
        return steps.size();
    }

    public String getPreviousStepKey() {
        return previousStepKey;
    }

    /**
     * 返回带更新前序步骤键的新上下文。
     *
     * @param nextPreviousStepKey 新前序步骤键
     * @return 新上下文
     */
    public PlanningStrategyContext withPreviousStepKey(String nextPreviousStepKey) {
        return new PlanningStrategyContext(planId,
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
                nextPreviousStepKey,
                true);
    }

    /**
     * 追加规划步骤。
     *
     * @param step 步骤对象
     * @param stepMeta 步骤元信息
     */
    public void appendStep(StepSpec step, Map<String, Object> stepMeta) {
        if (step != null) {
            steps.add(step);
        }
        if (stepMeta != null) {
            planSteps.add(stepMeta);
        }
    }

    /**
     * 追加步骤依赖。
     *
     * @param dependency 依赖映射
     */
    public void appendDependency(Map<String, String> dependency) {
        if (dependency == null || dependency.isEmpty()) {
            return;
        }
        dependencies.add(dependency);
    }
}
