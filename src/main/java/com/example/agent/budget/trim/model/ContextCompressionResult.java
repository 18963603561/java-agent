package com.example.agent.budget.trim.model;

import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 上下文压缩结果，用于回传触发情况与估算指标。
 */
public class ContextCompressionResult {

    /**
     * 更新后的上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 是否触发压缩。
     */
    private boolean triggered;

    /**
     * 是否因冷却时间跳过。
     */
    private boolean skippedCooldown;

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 裁剪前的令牌估算。
     */
    private Integer beforeTokens;

    /**
     * 裁剪后的令牌估算。
     */
    private Integer afterTrimTokens;

    /**
     * 压缩后的令牌估算。
     */
    private Integer afterCompressTokens;

    /**
     * 压缩耗时（毫秒）。
     */
    private Long durationMs;

    /**
     * 摘要版本。
     */
    private String summaryVersion;

    /**
     * 压缩后仍超预算。
     */
    private boolean stillOverBudget;

    /**
     * 压缩执行来源。
     */
    private String executionSource;

    /**
     * 压缩失败原因。
     */
    private String failureReason;

    /**
     * 是否发生降级。
     */
    private boolean fallbackApplied;

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
     * 最终生效执行来源。
     */
    private String winnerSource;

    /**
     * 回滚原因。
     */
    private String rollbackReason;

    /**
     * 本次质量分。
     */
    private Double qualityScore;

    /**
     * 是否命中自动回滚。
     */
    private boolean rollbackApplied;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public boolean isSkippedCooldown() {
        return skippedCooldown;
    }

    public void setSkippedCooldown(boolean skippedCooldown) {
        this.skippedCooldown = skippedCooldown;
    }

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

    public Integer getAfterTrimTokens() {
        return afterTrimTokens;
    }

    public void setAfterTrimTokens(Integer afterTrimTokens) {
        this.afterTrimTokens = afterTrimTokens;
    }

    public Integer getAfterCompressTokens() {
        return afterCompressTokens;
    }

    public void setAfterCompressTokens(Integer afterCompressTokens) {
        this.afterCompressTokens = afterCompressTokens;
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

    public boolean isStillOverBudget() {
        return stillOverBudget;
    }

    public void setStillOverBudget(boolean stillOverBudget) {
        this.stillOverBudget = stillOverBudget;
    }

    public String getExecutionSource() {
        return executionSource;
    }

    public void setExecutionSource(String executionSource) {
        this.executionSource = executionSource;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isFallbackApplied() {
        return fallbackApplied;
    }

    public void setFallbackApplied(boolean fallbackApplied) {
        this.fallbackApplied = fallbackApplied;
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

    public String getWinnerSource() {
        return winnerSource;
    }

    public void setWinnerSource(String winnerSource) {
        this.winnerSource = winnerSource;
    }

    public String getRollbackReason() {
        return rollbackReason;
    }

    public void setRollbackReason(String rollbackReason) {
        this.rollbackReason = rollbackReason;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public boolean isRollbackApplied() {
        return rollbackApplied;
    }

    public void setRollbackApplied(boolean rollbackApplied) {
        this.rollbackApplied = rollbackApplied;
    }
}
