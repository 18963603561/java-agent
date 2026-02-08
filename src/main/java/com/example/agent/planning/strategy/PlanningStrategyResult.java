package com.example.agent.planning.strategy;

import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略执行结果。
 */
public class PlanningStrategyResult {

    private final List<StepSpec> steps;
    private final List<Map<String, Object>> planSteps;
    private final List<Map<String, String>> dependencies;
    private final String scene;
    private final String nextStepKey;
    private final boolean terminal;

    private PlanningStrategyResult(List<StepSpec> steps,
                                   List<Map<String, Object>> planSteps,
                                   List<Map<String, String>> dependencies,
                                   String scene,
                                   String nextStepKey,
                                   boolean terminal) {
        this.steps = steps != null ? steps : List.of();
        this.planSteps = planSteps != null ? planSteps : List.of();
        this.dependencies = dependencies != null ? dependencies : List.of();
        this.scene = scene;
        this.nextStepKey = nextStepKey;
        this.terminal = terminal;
    }

    /**
     * 生成空结果。
     *
     * @return 空结果
     */
    public static PlanningStrategyResult empty() {
        return new PlanningStrategyResult(List.of(), List.of(), List.of(), null, null, false);
    }

    /**
     * 生成普通结果。
     *
     * @param steps 步骤
     * @param planSteps 计划步骤
     * @param dependencies 依赖关系
     * @param scene 场景
     * @param nextStepKey 下一步键
     * @return 策略结果
     */
    public static PlanningStrategyResult normal(List<StepSpec> steps,
                                                List<Map<String, Object>> planSteps,
                                                List<Map<String, String>> dependencies,
                                                String scene,
                                                String nextStepKey) {
        return new PlanningStrategyResult(cloneStepList(steps),
                clonePlanStepList(planSteps),
                cloneDependencyList(dependencies),
                scene,
                nextStepKey,
                false);
    }

    /**
     * 生成终止结果。
     *
     * @param steps 步骤
     * @param planSteps 计划步骤
     * @param dependencies 依赖关系
     * @param scene 场景
     * @param nextStepKey 下一步键
     * @return 策略结果
     */
    public static PlanningStrategyResult terminal(List<StepSpec> steps,
                                                  List<Map<String, Object>> planSteps,
                                                  List<Map<String, String>> dependencies,
                                                  String scene,
                                                  String nextStepKey) {
        return new PlanningStrategyResult(cloneStepList(steps),
                clonePlanStepList(planSteps),
                cloneDependencyList(dependencies),
                scene,
                nextStepKey,
                true);
    }

    public List<StepSpec> getSteps() {
        return steps;
    }

    public List<Map<String, Object>> getPlanSteps() {
        return planSteps;
    }

    public List<Map<String, String>> getDependencies() {
        return dependencies;
    }

    public String getScene() {
        return scene;
    }

    public String getNextStepKey() {
        return nextStepKey;
    }

    public boolean isTerminal() {
        return terminal;
    }

    private static List<StepSpec> cloneStepList(List<StepSpec> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    private static List<Map<String, Object>> clonePlanStepList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(values);
    }

    private static List<Map<String, String>> cloneDependencyList(List<Map<String, String>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(values);
    }
}

