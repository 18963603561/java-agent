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
     * 是否整形滑窗。
     */
    private boolean windowShaped;

    /**
     * 滑窗整形原因。
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
     * 是否注入摘要。
     */
    private boolean summaryInjected;

    /**
     * 摘要注入原因。
     */
    private String summaryInjectReason;

    /**
     * 是否启用双轨。
     */
    private boolean dualTrackEnabled;

    /**
     * 灰度版本。
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
