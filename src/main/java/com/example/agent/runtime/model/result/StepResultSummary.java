package com.example.agent.runtime.model.result;

import java.util.Map;

/**
 * 步骤摘要结构。
 */
public class StepResultSummary {

    private String text;
    private Map<String, Object> outputSummary;
    private Map<String, Object> toolResultSummary;
    private Map<String, Object> stepSummary;
    private Map<String, Object> inputSummary;
    private StepResultDigest inputDigest;
    private StepResultDigest outputDigest;
    private Boolean truncated;
    private String reason;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(Map<String, Object> outputSummary) {
        this.outputSummary = outputSummary;
    }

    public Map<String, Object> getToolResultSummary() {
        return toolResultSummary;
    }

    public void setToolResultSummary(Map<String, Object> toolResultSummary) {
        this.toolResultSummary = toolResultSummary;
    }

    public Map<String, Object> getStepSummary() {
        return stepSummary;
    }

    public void setStepSummary(Map<String, Object> stepSummary) {
        this.stepSummary = stepSummary;
    }

    public Map<String, Object> getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(Map<String, Object> inputSummary) {
        this.inputSummary = inputSummary;
    }

    public StepResultDigest getInputDigest() {
        return inputDigest;
    }

    public void setInputDigest(StepResultDigest inputDigest) {
        this.inputDigest = inputDigest;
    }

    public StepResultDigest getOutputDigest() {
        return outputDigest;
    }

    public void setOutputDigest(StepResultDigest outputDigest) {
        this.outputDigest = outputDigest;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
