package com.example.agent.runtime.model;

/**
 * 步骤结果引用集合。
 */
public class StepResultRefSet {

    private String rawRef;
    private String decisionRawRef;
    private String summaryRawRef;
    private String toolRawRef;
    private String modelRawRef;

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public String getDecisionRawRef() {
        return decisionRawRef;
    }

    public void setDecisionRawRef(String decisionRawRef) {
        this.decisionRawRef = decisionRawRef;
    }

    public String getSummaryRawRef() {
        return summaryRawRef;
    }

    public void setSummaryRawRef(String summaryRawRef) {
        this.summaryRawRef = summaryRawRef;
    }

    public String getToolRawRef() {
        return toolRawRef;
    }

    public void setToolRawRef(String toolRawRef) {
        this.toolRawRef = toolRawRef;
    }

    public String getModelRawRef() {
        return modelRawRef;
    }

    public void setModelRawRef(String modelRawRef) {
        this.modelRawRef = modelRawRef;
    }
}
