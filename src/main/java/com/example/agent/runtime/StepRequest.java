package com.example.agent.runtime;

import java.util.Map;

/**
 * 步骤执行请求，描述步骤输入与类型。
 */
public class StepRequest {

    private String stepType;
    private Map<String, Object> input;

    public StepRequest() {
    }

    public StepRequest(String stepType, Map<String, Object> input) {
        this.stepType = stepType;
        this.input = input;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }
}
