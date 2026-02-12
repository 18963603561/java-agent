package com.example.agent.capabilities.context.compression.contract;

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
     * 是否执行历史窗口整形。
     */
    private boolean windowShaped;

    /**
     * 历史窗口整形原因。
     */
    private String shapeReason;

    /**
     * 首段保留条数。
     */
    private Integer primersRetained;

    /**
     * 尾段保留条数。
     */
    private Integer recentsRetained;

    /**
     * 中段窗口条数。
     */
    private Integer middleWindowSize;

    /**
     * 是否注入压缩摘要到提示装配。
     */
    private boolean summaryInjected;

    /**
     * 摘要注入原因。
     */
    private String summaryInjectReason;

    /**
     * 是否启用双轨实验。
     */
    private boolean dualTrackEnabled;

    /**
     * 灰度策略版本。
     */
    private String rolloutVersion;

    /**
     * 灰度决策原因。
     */
    private String rolloutReason;

    /**
     * 主轨来源。
     */
    private String primarySource;

    /**
     * 影子轨来源。
     */
    private String shadowSource;

    /**
     * 对比记录标识。
     */
    private String comparisonRecordId;

    /**
     * 回滚原因。
     */
    private String rollbackReason;

    /**
     * 胜出来源。
     */
    private String winnerSource;

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

    public boolean isWindowShaped() {
        return windowShaped;
    }

    public void setWindowShaped(boolean windowShaped) {
        this.windowShaped = windowShaped;
    }

    public String getShapeReason() {
        return shapeReason;
    }

    public void setShapeReason(String shapeReason) {
        this.shapeReason = shapeReason;
    }

    public Integer getPrimersRetained() {
        return primersRetained;
    }

    public void setPrimersRetained(Integer primersRetained) {
        this.primersRetained = primersRetained;
    }

    public Integer getRecentsRetained() {
        return recentsRetained;
    }

    public void setRecentsRetained(Integer recentsRetained) {
        this.recentsRetained = recentsRetained;
    }

    public Integer getMiddleWindowSize() {
        return middleWindowSize;
    }

    public void setMiddleWindowSize(Integer middleWindowSize) {
        this.middleWindowSize = middleWindowSize;
    }

    public boolean isSummaryInjected() {
        return summaryInjected;
    }

    public void setSummaryInjected(boolean summaryInjected) {
        this.summaryInjected = summaryInjected;
    }

    public String getSummaryInjectReason() {
        return summaryInjectReason;
    }

    public void setSummaryInjectReason(String summaryInjectReason) {
        this.summaryInjectReason = summaryInjectReason;
    }

    public boolean isDualTrackEnabled() {
        return dualTrackEnabled;
    }

    public void setDualTrackEnabled(boolean dualTrackEnabled) {
        this.dualTrackEnabled = dualTrackEnabled;
    }

    public String getRolloutVersion() {
        return rolloutVersion;
    }

    public void setRolloutVersion(String rolloutVersion) {
        this.rolloutVersion = rolloutVersion;
    }

    public String getRolloutReason() {
        return rolloutReason;
    }

    public void setRolloutReason(String rolloutReason) {
        this.rolloutReason = rolloutReason;
    }

    public String getPrimarySource() {
        return primarySource;
    }

    public void setPrimarySource(String primarySource) {
        this.primarySource = primarySource;
    }

    public String getShadowSource() {
        return shadowSource;
    }

    public void setShadowSource(String shadowSource) {
        this.shadowSource = shadowSource;
    }

    public String getComparisonRecordId() {
        return comparisonRecordId;
    }

    public void setComparisonRecordId(String comparisonRecordId) {
        this.comparisonRecordId = comparisonRecordId;
    }

    public String getRollbackReason() {
        return rollbackReason;
    }

    public void setRollbackReason(String rollbackReason) {
        this.rollbackReason = rollbackReason;
    }

    public String getWinnerSource() {
        return winnerSource;
    }

    public void setWinnerSource(String winnerSource) {
        this.winnerSource = winnerSource;
    }
}
