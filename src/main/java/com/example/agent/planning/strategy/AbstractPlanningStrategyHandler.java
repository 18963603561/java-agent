package com.example.agent.planning.strategy;

import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.planning.parser.PlanParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规划策略处理器抽象基类。
 */
public abstract class AbstractPlanningStrategyHandler implements PlanningStrategyHandler {

    protected final PlanParser planParser;

    /**
     * 构造策略处理器。
     *
     * @param planParser 步骤解析器
     */
    protected AbstractPlanningStrategyHandler(PlanParser planParser) {
        this.planParser = planParser;
    }

    /**
     * 生成当前步骤键。
     *
     * @param context 策略上下文
     * @return 步骤键
     */
    protected String nextStepKey(PlanningStrategyContext context) {
        if (context == null || context.getPreviousStepKey() == null) {
            return "step-1";
        }
        return "step-" + (context.getStepCount() + 1);
    }

    /**
     * 追加依赖关系。
     *
     * @param context 策略上下文
     * @param stepKey 当前步骤键
     */
    protected void addDependency(PlanningStrategyContext context, String stepKey) {
        if (context == null || context.getPreviousStepKey() == null || stepKey == null) {
            return;
        }
        context.appendDependency(Map.of(
                PlanningFieldKeys.FROM,
                context.getPreviousStepKey(),
                PlanningFieldKeys.TO,
                stepKey));
    }

    /**
     * 构造步骤元信息。
     *
     * @param stepKey 步骤键
     * @param stepType 步骤类型
     * @param name 展示名称
     * @return 元信息
     */
    protected Map<String, Object> buildPlanStepMeta(String stepKey, String stepType, String name) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(PlanningFieldKeys.ID, stepKey);
        meta.put(PlanningFieldKeys.TYPE, stepType);
        meta.put(PlanningFieldKeys.NAME, name);
        return meta;
    }

    /**
     * 追加步骤与元信息。
     *
     * @param context 策略上下文
     * @param step 步骤
     * @param stepMeta 元信息
     */
    protected void appendStep(PlanningStrategyContext context,
                              com.example.agent.runtime.model.StepSpec step,
                              Map<String, Object> stepMeta) {
        if (context == null || step == null) {
            return;
        }
        context.appendStep(step, stepMeta);
    }

    /**
     * 生成终止策略结果。
     *
     * @param context 策略上下文
     * @param scene 场景
     * @param stepKey 当前步骤键
     * @return 终止结果
     */
    protected PlanningStrategyResult terminal(PlanningStrategyContext context, String scene, String stepKey) {
        return PlanningStrategyResult.terminal(
                context.getSteps(),
                context.getPlanSteps(),
                context.getDependencies(),
                scene,
                stepKey);
    }

    /**
     * 生成非终止策略结果。
     *
     * @param context 策略上下文
     * @param scene 场景
     * @param stepKey 当前步骤键
     * @return 非终止结果
     */
    protected PlanningStrategyResult normal(PlanningStrategyContext context, String scene, String stepKey) {
        return PlanningStrategyResult.normal(
                context.getSteps(),
                context.getPlanSteps(),
                context.getDependencies(),
                scene,
                stepKey);
    }

    /**
     * 生成空结果。
     *
     * @return 空结果
     */
    protected PlanningStrategyResult empty() {
        return PlanningStrategyResult.empty();
    }

    protected boolean equalsAnyIgnoreCase(String value, List<String> candidates) {
        if (value == null || value.isBlank() || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
