package com.example.agent.reflection;

import java.util.Map;

/**
 * 反思请求，描述步骤输出与上下文。
 */
public class ReflectionRequest {

    private String stepId;
    private Map<String, Object> output;

    public ReflectionRequest() {
    }

    public ReflectionRequest(String stepId, Map<String, Object> output) {
        this.stepId = stepId;
        this.output = output;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }
}
