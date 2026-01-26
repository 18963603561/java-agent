package com.example.agent.planning;

import com.example.agent.runtime.StepRequest;
import java.util.List;

/**
 * 规划结果，包含步骤列表与摘要说明。
 */
public class PlanResult {

    private String planId;
    private String summary;
    private List<StepRequest> steps;

    public PlanResult() {
    }

    public PlanResult(String planId, String summary, List<StepRequest> steps) {
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

    public List<StepRequest> getSteps() {
        return steps;
    }

    public void setSteps(List<StepRequest> steps) {
        this.steps = steps;
    }
}
