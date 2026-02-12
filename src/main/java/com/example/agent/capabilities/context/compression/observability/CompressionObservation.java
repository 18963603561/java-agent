package com.example.agent.capabilities.context.compression.observability;

/**
 * 压缩观测语义对象。
 *
 * <p>用途：统一表达压缩链路各阶段观测字段，供指标、日志与事件复用。</p>
 */
public class CompressionObservation {

    /**
     * 观测阶段。
     */
    private String stage;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 会话标识。
     */
    private String sessionId;

    /**
     * 压缩来源。
     */
    private String source;

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 窗口整形原因。
     */
    private String shapeReason;

    /**
     * 摘要注入原因。
     */
    private String summaryInjectReason;

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 是否发生降级。
     */
    private Boolean fallbackApplied;

    /**
     * 是否执行窗口整形。
     */
    private Boolean windowShaped;

    /**
     * 是否注入摘要。
     */
    private Boolean summaryInjected;

    /**
     * 是否启用双轨。
     */
    private Boolean dualTrackEnabled;

    /**
     * 灰度版本。
     */
    private String rolloutVersion;

    /**
     * 主轨来源。
     */
    private String primarySource;

    /**
     * 影子轨来源。
     */
    private String shadowSource;

    /**
     * 胜出来源。
     */
    private String winnerSource;

    /**
     * 回滚原因。
     */
    private String rollbackReason;

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getShapeReason() {
        return shapeReason;
    }

    public void setShapeReason(String shapeReason) {
        this.shapeReason = shapeReason;
    }

    public String getSummaryInjectReason() {
        return summaryInjectReason;
    }

    public void setSummaryInjectReason(String summaryInjectReason) {
        this.summaryInjectReason = summaryInjectReason;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Boolean getFallbackApplied() {
        return fallbackApplied;
    }

    public void setFallbackApplied(Boolean fallbackApplied) {
        this.fallbackApplied = fallbackApplied;
    }

    public Boolean getWindowShaped() {
        return windowShaped;
    }

    public void setWindowShaped(Boolean windowShaped) {
        this.windowShaped = windowShaped;
    }

    public Boolean getSummaryInjected() {
        return summaryInjected;
    }

    public void setSummaryInjected(Boolean summaryInjected) {
        this.summaryInjected = summaryInjected;
    }

    public Boolean getDualTrackEnabled() {
        return dualTrackEnabled;
    }

    public void setDualTrackEnabled(Boolean dualTrackEnabled) {
        this.dualTrackEnabled = dualTrackEnabled;
    }

    public String getRolloutVersion() {
        return rolloutVersion;
    }

    public void setRolloutVersion(String rolloutVersion) {
        this.rolloutVersion = rolloutVersion;
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
}
