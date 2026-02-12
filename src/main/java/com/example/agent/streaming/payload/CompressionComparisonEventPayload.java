package com.example.agent.streaming.payload;

/**
 * 压缩双轨对比事件载荷。
 */
public class CompressionComparisonEventPayload {

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 快照标识。
     */
    private String snapshotId;

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
    private String winner;

    /**
     * 回滚原因。
     */
    private String rollbackReason;

    /**
     * 质量分。
     */
    private Double qualityScore;

    /**
     * 对比记录标识。
     */
    private String comparisonRecordId;

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

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
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

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
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

    public String getComparisonRecordId() {
        return comparisonRecordId;
    }

    public void setComparisonRecordId(String comparisonRecordId) {
        this.comparisonRecordId = comparisonRecordId;
    }
}
