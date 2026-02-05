package com.example.agent.planning;

import com.example.agent.runtime.model.plan.StepSpec;
import java.util.List;

/**
 * 规划实体，描述步骤集合与依赖关系。
 */
public class Plan {

    private String planId;
    private List<StepSpec> steps;

    public Plan() {
    }

    public Plan(String planId, List<StepSpec> steps) {
        this.planId = planId;
        this.steps = steps;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public List<StepSpec> getSteps() {
        return steps;
    }

    public void setSteps(List<StepSpec> steps) {
        this.steps = steps;
    }
}
