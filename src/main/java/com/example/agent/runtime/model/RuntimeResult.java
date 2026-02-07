package com.example.agent.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * 运行时结果对象。
 *
 * <p>用途：汇总规划标识、规划摘要、步骤结果与最终输出。
 * <p>输入：由运行时收口阶段组装填充。
 * <p>输出：供任务编排、记忆写入与接口层返回使用。
 * <p>边界：字段均允许为空以覆盖空规划或执行失败场景。
 */
public class RuntimeResult {

    /**
     * 规划标识。
     */
    private String planId;

    /**
     * 规划摘要。
     */
    private String planSummary;

    /**
     * 步骤结果列表。
     */
    private List<StepResult> steps;

    /**
     * 最终输出。
     */
    private Map<String, Object> finalOutput;

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public void setPlanSummary(String planSummary) {
        this.planSummary = planSummary;
    }

    public List<StepResult> getSteps() {
        return steps;
    }

    public void setSteps(List<StepResult> steps) {
        this.steps = steps;
    }

    public Map<String, Object> getFinalOutput() {
        return finalOutput;
    }

    public void setFinalOutput(Map<String, Object> finalOutput) {
        this.finalOutput = finalOutput;
    }
}
