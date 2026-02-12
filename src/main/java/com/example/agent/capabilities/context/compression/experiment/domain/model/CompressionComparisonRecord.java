package com.example.agent.capabilities.context.compression.experiment.domain.model;

import java.time.Instant;

/**
 * 压缩双轨对比记录。
 *
 * <p>用途：沉淀主轨与影子轨的执行结果对比信息，支持审计、统计与回放。</p>
 */
public class CompressionComparisonRecord {

    /**
     * 记录标识。
     */
    private String recordId;

    /**
     * 记录时间。
     */
    private Instant timestamp;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 会话标识。
     */
    private String sessionId;

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
     * 主轨是否成功。
     */
    private boolean primarySuccess;

    /**
     * 影子轨是否成功。
     */
    private boolean shadowSuccess;

    /**
     * 主轨失败原因。
     */
    private String primaryFailureReason;

    /**
     * 影子轨失败原因。
     */
    private String shadowFailureReason;

    /**
     * 主轨压缩前令牌数。
     */
    private Integer primaryOriginalTokens;

    /**
     * 主轨压缩后令牌数。
     */
    private Integer primaryCompressedTokens;

    /**
     * 影子轨压缩前令牌数。
     */
    private Integer shadowOriginalTokens;

    /**
     * 影子轨压缩后令牌数。
     */
    private Integer shadowCompressedTokens;

    /**
     * 质量评分。
     */
    private Double qualityScore;

    /**
     * 是否触发回滚。
     */
    private boolean rollbackApplied;

    /**
     * 回滚原因。
     */
    private String rollbackReason;

    /**
     * 胜出来源。
     */
    private String winnerSource;

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public boolean isPrimarySuccess() {
        return primarySuccess;
    }

    public void setPrimarySuccess(boolean primarySuccess) {
        this.primarySuccess = primarySuccess;
    }

    public boolean isShadowSuccess() {
        return shadowSuccess;
    }

    public void setShadowSuccess(boolean shadowSuccess) {
        this.shadowSuccess = shadowSuccess;
    }

    public String getPrimaryFailureReason() {
        return primaryFailureReason;
    }

    public void setPrimaryFailureReason(String primaryFailureReason) {
        this.primaryFailureReason = primaryFailureReason;
    }

    public String getShadowFailureReason() {
        return shadowFailureReason;
    }

    public void setShadowFailureReason(String shadowFailureReason) {
        this.shadowFailureReason = shadowFailureReason;
    }

    public Integer getPrimaryOriginalTokens() {
        return primaryOriginalTokens;
    }

    public void setPrimaryOriginalTokens(Integer primaryOriginalTokens) {
        this.primaryOriginalTokens = primaryOriginalTokens;
    }

    public Integer getPrimaryCompressedTokens() {
        return primaryCompressedTokens;
    }

    public void setPrimaryCompressedTokens(Integer primaryCompressedTokens) {
        this.primaryCompressedTokens = primaryCompressedTokens;
    }

    public Integer getShadowOriginalTokens() {
        return shadowOriginalTokens;
    }

    public void setShadowOriginalTokens(Integer shadowOriginalTokens) {
        this.shadowOriginalTokens = shadowOriginalTokens;
    }

    public Integer getShadowCompressedTokens() {
        return shadowCompressedTokens;
    }

    public void setShadowCompressedTokens(Integer shadowCompressedTokens) {
        this.shadowCompressedTokens = shadowCompressedTokens;
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
