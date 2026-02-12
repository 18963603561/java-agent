package com.example.agent.streaming.payload;

/**
 * 压缩阶段事件载荷。
 *
 * <p>用途：表达压缩阶段的稳定事件契约，避免在通用快照事件中散列压缩字段。</p>
 */
public class CompressionStageEventPayload {

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
     * 上下文阶段。
     */
    private ContextSnapshotStage stage;

    /**
     * 压缩摘要。
     */
    private ContextCompressionSummary compressionSummary;

    /**
     * 证据包是否存在。
     */
    private Boolean evidencePackPresent;

    /**
     * 工具证据数量。
     */
    private Integer evidenceToolCount;

    /**
     * 记忆证据数量。
     */
    private Integer evidenceMemoryCount;

    /**
     * 研究证据数量。
     */
    private Integer evidenceResearchCount;

    /**
     * 裁剪证据数量。
     */
    private Integer evidenceTruncationCount;

    /**
     * 证据体积估算字符数。
     */
    private Integer evidenceApproxChars;

    /**
     * 证据包版本号。
     */
    private String evidencePackVersion;

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

    public ContextSnapshotStage getStage() {
        return stage;
    }

    public void setStage(ContextSnapshotStage stage) {
        this.stage = stage;
    }

    public ContextCompressionSummary getCompressionSummary() {
        return compressionSummary;
    }

    public void setCompressionSummary(ContextCompressionSummary compressionSummary) {
        this.compressionSummary = compressionSummary;
    }

    public Boolean getEvidencePackPresent() {
        return evidencePackPresent;
    }

    public void setEvidencePackPresent(Boolean evidencePackPresent) {
        this.evidencePackPresent = evidencePackPresent;
    }

    public Integer getEvidenceToolCount() {
        return evidenceToolCount;
    }

    public void setEvidenceToolCount(Integer evidenceToolCount) {
        this.evidenceToolCount = evidenceToolCount;
    }

    public Integer getEvidenceMemoryCount() {
        return evidenceMemoryCount;
    }

    public void setEvidenceMemoryCount(Integer evidenceMemoryCount) {
        this.evidenceMemoryCount = evidenceMemoryCount;
    }

    public Integer getEvidenceResearchCount() {
        return evidenceResearchCount;
    }

    public void setEvidenceResearchCount(Integer evidenceResearchCount) {
        this.evidenceResearchCount = evidenceResearchCount;
    }

    public Integer getEvidenceTruncationCount() {
        return evidenceTruncationCount;
    }

    public void setEvidenceTruncationCount(Integer evidenceTruncationCount) {
        this.evidenceTruncationCount = evidenceTruncationCount;
    }

    public Integer getEvidenceApproxChars() {
        return evidenceApproxChars;
    }

    public void setEvidenceApproxChars(Integer evidenceApproxChars) {
        this.evidenceApproxChars = evidenceApproxChars;
    }

    public String getEvidencePackVersion() {
        return evidencePackVersion;
    }

    public void setEvidencePackVersion(String evidencePackVersion) {
        this.evidencePackVersion = evidencePackVersion;
    }
}

