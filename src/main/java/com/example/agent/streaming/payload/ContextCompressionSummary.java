package com.example.agent.streaming.payload;

/**
 * 上下文压缩摘要，用于事件载荷中的压缩统计。
 */
public class ContextCompressionSummary {

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 压缩前 token 估算。
     */
    private Integer beforeTokens;

    /**
     * 压缩后 token 估算。
     */
    private Integer afterTokens;

    /**
     * 压缩耗时（毫秒）。
     */
    private Long durationMs;

    /**
     * 摘要版本号。
     */
    private String summaryVersion;

    /**
     * 压缩最终生效来源。
     */
    private String winnerSource;

    /**
     * 是否应用自动回滚。
     */
    private Boolean rollbackApplied;

    /**
     * 自动回滚原因。
     */
    private String rollbackReason;

    /**
     * 灰度版本。
     */
    private String rolloutVersion;

    /**
     * 质量门禁版本。
     */
    private String qualityGateVersion;

    /**
     * 回滚策略版本。
     */
    private String rollbackPolicyVersion;

    /**
     * 质量评分。
     */
    private Double qualityScore;

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public Integer getBeforeTokens() {
        return beforeTokens;
    }

    public void setBeforeTokens(Integer beforeTokens) {
        this.beforeTokens = beforeTokens;
    }

    public Integer getAfterTokens() {
        return afterTokens;
    }

    public void setAfterTokens(Integer afterTokens) {
        this.afterTokens = afterTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public String getWinnerSource() {
        return winnerSource;
    }

    public void setWinnerSource(String winnerSource) {
        this.winnerSource = winnerSource;
    }

    public Boolean getRollbackApplied() {
        return rollbackApplied;
    }

    public void setRollbackApplied(Boolean rollbackApplied) {
        this.rollbackApplied = rollbackApplied;
    }

    public String getRollbackReason() {
        return rollbackReason;
    }

    public void setRollbackReason(String rollbackReason) {
        this.rollbackReason = rollbackReason;
    }

    public String getRolloutVersion() {
        return rolloutVersion;
    }

    public void setRolloutVersion(String rolloutVersion) {
        this.rolloutVersion = rolloutVersion;
    }

    public String getQualityGateVersion() {
        return qualityGateVersion;
    }

    public void setQualityGateVersion(String qualityGateVersion) {
        this.qualityGateVersion = qualityGateVersion;
    }

    public String getRollbackPolicyVersion() {
        return rollbackPolicyVersion;
    }

    public void setRollbackPolicyVersion(String rollbackPolicyVersion) {
        this.rollbackPolicyVersion = rollbackPolicyVersion;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }
}
