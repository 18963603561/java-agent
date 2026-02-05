package com.example.agent.runtime.structured;

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
     * 告警列表。
     */
    private List<String> warnings;

    /**
     * 是否截断。
     */
    private Boolean truncated;

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}
