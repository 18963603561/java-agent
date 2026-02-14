package com.example.agent.runtime.structured.result;

import java.util.List;

/**
 * 结构化质量信息。
 */
public class StructuredQuality {

    /**
     * 置信度。
     */
    private Double confidence;

    /**
     * 完整度。
     */
    private Double completeness;

    /**
     * 告警列表。
     */
    private List<String> warnings;

    /**
     * 缺失字段列表。
     */
    private List<String> missingFields;

    /**
     * 是否截断。
     */
    private boolean truncated;

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Double getCompleteness() {
        return completeness;
    }

    public void setCompleteness(Double completeness) {
        this.completeness = completeness;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(List<String> missingFields) {
        this.missingFields = missingFields;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
