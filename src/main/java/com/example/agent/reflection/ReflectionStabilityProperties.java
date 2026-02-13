package com.example.agent.reflection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 反思稳定性配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.reflection.stability")
public class ReflectionStabilityProperties {

    /**
     * 是否启用稳定性评分。
     */
    private boolean enable = true;

    /**
     * 稳定性最低评分阈值。
     */
    private double minScore = 0.6D;

    /**
     * 摘要最小字符数。
     */
    private int minSummaryChars = 40;

    /**
     * 结果最小键数量。
     */
    private int minResultKeys = 1;

    /**
     * 摘要缺失惩罚系数。
     */
    private double summaryPenalty = 0.4D;

    /**
     * 结果缺失惩罚系数。
     */
    private double resultPenalty = 0.3D;

    /**
     * 指纹缺失惩罚系数。
     */
    private double digestPenalty = 0.2D;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public int getMinSummaryChars() {
        return minSummaryChars;
    }

    public void setMinSummaryChars(int minSummaryChars) {
        this.minSummaryChars = minSummaryChars;
    }

    public int getMinResultKeys() {
        return minResultKeys;
    }

    public void setMinResultKeys(int minResultKeys) {
        this.minResultKeys = minResultKeys;
    }

    public double getSummaryPenalty() {
        return summaryPenalty;
    }

    public void setSummaryPenalty(double summaryPenalty) {
        this.summaryPenalty = summaryPenalty;
    }

    public double getResultPenalty() {
        return resultPenalty;
    }

    public void setResultPenalty(double resultPenalty) {
        this.resultPenalty = resultPenalty;
    }

    public double getDigestPenalty() {
        return digestPenalty;
    }

    public void setDigestPenalty(double digestPenalty) {
        this.digestPenalty = digestPenalty;
    }
}
