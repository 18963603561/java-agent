package com.example.agent.runtime.summary.audit;

import java.util.Map;

/**
 * 语义摘要抽检记录。
 */
public class SemanticSummaryAuditRecord {

    /**
     * 原始引用。
     */
    private String rawRef;

    /**
     * 结构化结果引用。
     */
    private Map<String, Object> result;

    /**
     * 摘要映射。
     */
    private Map<String, Object> summary;

    /**
     * 质量映射。
     */
    private Map<String, Object> quality;

    /**
     * 步骤标识。
     */
    private String stepId;

    /**
     * 步骤类型。
     */
    private String stepType;

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 记录时间。
     */
    private String createdAt;

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public Map<String, Object> getQuality() {
        return quality;
    }

    public void setQuality(Map<String, Object> quality) {
        this.quality = quality;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
