package com.example.agent.planning;

import com.example.agent.runtime.model.StepSpec;
import java.util.List;

/**
 * 规划结果，包含步骤列表与摘要说明。
 */
public class PlanResult {

    private String planId;
    private String summary;
    private List<StepSpec> steps;

    public PlanResult() {
    }

    public PlanResult(String planId, String summary, List<StepSpec> steps) {
        this.planId = planId;
        this.summary = summary;
        this.steps = steps;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<StepSpec> getSteps() {
        return steps;
    }

    public void setSteps(List<StepSpec> steps) {
        this.steps = steps;
    }

    /**
     * 创建只读规划结果实例。
     *
     * @param planId 规划标识
     * @param summary 规划摘要
     * @param steps 规划步骤
     * @return 新的规划结果
     */
    public static PlanResult readonly(String planId, String summary, List<StepSpec> steps) {
        return new PlanResult(planId,
                summary,
                steps != null ? List.copyOf(steps) : List.of());
    }
}
