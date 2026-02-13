package com.example.agent.runtime.structured;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 结构化质量配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.structured.quality")
public class StructuredQualityProperties {

    /**
     * 是否启用质量评分。
     */
    private boolean enable = true;

    /**
     * 默认置信度。
     */
    private double baseConfidence = 0.9D;

    /**
     * 错误类结果默认置信度。
     */
    private double errorConfidence = 0.2D;

    /**
     * 数据为空时置信度。
     */
    private double emptyDataConfidence = 0.1D;

    /**
     * 缺失字段惩罚系数。
     */
    private double missingPenalty = 0.2D;

    /**
     * 完整度低阈值。
     */
    private double lowCompletenessThreshold = 0.6D;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public double getBaseConfidence() {
        return baseConfidence;
    }

    public void setBaseConfidence(double baseConfidence) {
        this.baseConfidence = baseConfidence;
    }

    public double getErrorConfidence() {
        return errorConfidence;
    }

    public void setErrorConfidence(double errorConfidence) {
        this.errorConfidence = errorConfidence;
    }

    public double getEmptyDataConfidence() {
        return emptyDataConfidence;
    }

    public void setEmptyDataConfidence(double emptyDataConfidence) {
        this.emptyDataConfidence = emptyDataConfidence;
    }

    public double getMissingPenalty() {
        return missingPenalty;
    }

    public void setMissingPenalty(double missingPenalty) {
        this.missingPenalty = missingPenalty;
    }

    public double getLowCompletenessThreshold() {
        return lowCompletenessThreshold;
    }

    public void setLowCompletenessThreshold(double lowCompletenessThreshold) {
        this.lowCompletenessThreshold = lowCompletenessThreshold;
    }
}
