package com.example.agent.runtime.structured.result;

import java.util.List;

/**
 * 结构化引用信息。
 */
public class StructuredRefs {

    /**
     * 原始结果引用。
     */
    private String rawRef;

    /**
     * 决策阶段模型原始引用。
     */
    private String decisionRawRef;

    /**
     * 总结阶段模型原始引用。
     */
    private String summaryRawRef;

    /**
     * 工具执行原始引用。
     */
    private String toolRawRef;

    /**
     * 通用模型原始引用。
     */
    private String modelRawRef;

    /**
     * 证据标识列表。
     */
    private List<String> evidenceIds;

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

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<String> evidenceIds) {
        this.evidenceIds = evidenceIds;
    }
}
