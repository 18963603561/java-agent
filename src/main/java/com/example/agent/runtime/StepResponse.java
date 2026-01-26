package com.example.agent.runtime;

import java.util.Map;

/**
 * 步骤执行响应，包含输出与状态。
 */
public class StepResponse {

    private String stepId;
    private StepState status;
    private Map<String, Object> output;
    private String errorCode;

    public StepResponse() {
    }

    public StepResponse(String stepId, StepState status, Map<String, Object> output, String errorCode) {
        this.stepId = stepId;
        this.status = status;
        this.output = output;
        this.errorCode = errorCode;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public StepState getStatus() {
        return status;
    }

    public void setStatus(StepState status) {
        this.status = status;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
