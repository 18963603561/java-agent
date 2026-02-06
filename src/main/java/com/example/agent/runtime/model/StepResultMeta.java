package com.example.agent.runtime.model;

import com.example.agent.runtime.step.StepState;

/**
 * 步骤结果元信息。
 */
public class StepResultMeta {

    private String stepId;
    private long seq;
    private String type;
    private StepState status;
    private String toolName;
    private String modelId;
    private Integer attempt;
    private StepResultTiming timing;

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public StepState getStatus() {
        return status;
    }

    public void setStatus(StepState status) {
        this.status = status;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public StepResultTiming getTiming() {
        return timing;
    }

    public void setTiming(StepResultTiming timing) {
        this.timing = timing;
    }
}
