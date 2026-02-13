package com.example.agent.runtime.summary.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语义摘要抽检配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.summary.audit")
public class SemanticSummaryAuditProperties {

    /**
     * 是否启用抽检。
     */
    private boolean enable = false;

    /**
     * 抽检比例。
     */
    private double sampleRate = 0.02D;

    /**
     * 低质量阈值。
     */
    private double lowScoreThreshold = 0.6D;

    /**
     * 是否强制抽检低质量摘要。
     */
    private boolean forceLowScore = true;

    /**
     * 结果字段最大保留键数。
     */
    private int maxResultKeys = 50;

    /**
     * 结果数据字段最大保留键数。
     */
    private int maxDataKeys = 20;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public double getLowScoreThreshold() {
        return lowScoreThreshold;
    }

    public void setLowScoreThreshold(double lowScoreThreshold) {
        this.lowScoreThreshold = lowScoreThreshold;
    }

    public boolean isForceLowScore() {
        return forceLowScore;
    }

    public void setForceLowScore(boolean forceLowScore) {
        this.forceLowScore = forceLowScore;
    }

    public int getMaxResultKeys() {
        return maxResultKeys;
    }

    public void setMaxResultKeys(int maxResultKeys) {
        this.maxResultKeys = maxResultKeys;
    }

    public int getMaxDataKeys() {
        return maxDataKeys;
    }

    public void setMaxDataKeys(int maxDataKeys) {
        this.maxDataKeys = maxDataKeys;
    }
}
