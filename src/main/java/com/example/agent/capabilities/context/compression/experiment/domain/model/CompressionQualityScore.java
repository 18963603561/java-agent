package com.example.agent.capabilities.context.compression.experiment.domain.model;

/**
 * 压缩质量评分结果。
 */
public class CompressionQualityScore {

    /**
     * 质量总分（0-100）。
     */
    private double score;

    /**
     * 主轨压缩率。
     */
    private Double primaryCompressionRate;

    /**
     * 影子轨压缩率。
     */
    private Double shadowCompressionRate;

    /**
     * 主轨相对影子轨的退化率。
     */
    private double degradationRate;

    /**
     * 是否建议影子轨胜出。
     */
    private boolean shadowPreferred;

    /**
     * 评分原因。
     */
    private String reason;

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Double getPrimaryCompressionRate() {
        return primaryCompressionRate;
    }

    public void setPrimaryCompressionRate(Double primaryCompressionRate) {
        this.primaryCompressionRate = primaryCompressionRate;
    }

    public Double getShadowCompressionRate() {
        return shadowCompressionRate;
    }

    public void setShadowCompressionRate(Double shadowCompressionRate) {
        this.shadowCompressionRate = shadowCompressionRate;
    }

    public double getDegradationRate() {
        return degradationRate;
    }

    public void setDegradationRate(double degradationRate) {
        this.degradationRate = degradationRate;
    }

    public boolean isShadowPreferred() {
        return shadowPreferred;
    }

    public void setShadowPreferred(boolean shadowPreferred) {
        this.shadowPreferred = shadowPreferred;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
