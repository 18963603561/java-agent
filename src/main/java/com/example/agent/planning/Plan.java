package com.example.agent.planning;

import com.example.agent.runtime.StepRequest;
import java.util.List;

/**
 * 规划实体，描述步骤集合与依赖关系。
 */
public class Plan {

    private String planId;
    private List<StepRequest> steps;

    public Plan() {
    }

    public Plan(String planId, List<StepRequest> steps) {
        this.planId = planId;
        this.steps = steps;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public List<StepRequest> getSteps() {
        return steps;
    }

    public void setSteps(List<StepRequest> steps) {
        this.steps = steps;
    }
}
