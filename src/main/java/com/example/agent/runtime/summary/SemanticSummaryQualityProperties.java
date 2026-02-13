package com.example.agent.runtime.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语义摘要质量配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.summary.quality")
public class SemanticSummaryQualityProperties {

    /**
     * 是否启用质量评分。
     */
    private boolean enable = true;

    /**
     * 评分低于该阈值时视为低质量。
     */
    private double minScore = 0.6;

    /**
     * 摘要文本最小字符数。
     */
    private int minChars = 80;

    /**
     * 覆盖度低阈值。
     */
    private double lowCoverageThreshold = 0.3;

    /**
     * 缺失字段惩罚系数。
     */
    private double missingFieldPenalty = 0.15;

    /**
     * 截断惩罚系数。
     */
    private double truncatedPenalty = 0.1;

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

    public int getMinChars() {
        return minChars;
    }

    public void setMinChars(int minChars) {
        this.minChars = minChars;
    }

    public double getLowCoverageThreshold() {
        return lowCoverageThreshold;
    }

    public void setLowCoverageThreshold(double lowCoverageThreshold) {
        this.lowCoverageThreshold = lowCoverageThreshold;
    }

    public double getMissingFieldPenalty() {
        return missingFieldPenalty;
    }

    public void setMissingFieldPenalty(double missingFieldPenalty) {
        this.missingFieldPenalty = missingFieldPenalty;
    }

    public double getTruncatedPenalty() {
        return truncatedPenalty;
    }

    public void setTruncatedPenalty(double truncatedPenalty) {
        this.truncatedPenalty = truncatedPenalty;
    }
}
