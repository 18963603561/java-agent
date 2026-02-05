package com.example.agent.runtime;

import com.example.agent.runtime.model.result.StepResult;
import java.util.List;
import java.util.Map;

/**
 * 运行时结果，汇总规划、步骤与最终输出。
 */
public class RuntimeResult {

    private String planId;
    private String planSummary;
    private List<StepResult> steps;
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
