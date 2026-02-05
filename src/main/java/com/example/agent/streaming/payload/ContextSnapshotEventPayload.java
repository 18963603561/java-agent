package com.example.agent.streaming.payload;

import java.util.List;

/**
 * 上下文快照阶段事件载荷，字段均可选。
 */
public class ContextSnapshotEventPayload {

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
     * 事件阶段。
     */
    private ContextSnapshotStage stage;

    /**
     * 预算摘要，可为空。
     */
    private ContextBudgetSummary budgetSummary;

    /**
     * 裁剪摘要，可为空。
     */
    private ContextTrimSummary trimSummary;

    /**
     * 压缩摘要，可为空。
     */
    private ContextCompressionSummary compressionSummary;

    /**
     * 提示词裁剪段落标识列表。
     */
    private List<String> promptTruncatedSections;

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
     * 证据包体积估算字符数。
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

    public ContextBudgetSummary getBudgetSummary() {
        return budgetSummary;
    }

    public void setBudgetSummary(ContextBudgetSummary budgetSummary) {
        this.budgetSummary = budgetSummary;
    }

    public ContextTrimSummary getTrimSummary() {
        return trimSummary;
    }

    public void setTrimSummary(ContextTrimSummary trimSummary) {
        this.trimSummary = trimSummary;
    }

    public ContextCompressionSummary getCompressionSummary() {
        return compressionSummary;
    }

    public void setCompressionSummary(ContextCompressionSummary compressionSummary) {
        this.compressionSummary = compressionSummary;
    }

    public List<String> getPromptTruncatedSections() {
        return promptTruncatedSections;
    }

    public void setPromptTruncatedSections(List<String> promptTruncatedSections) {
        this.promptTruncatedSections = promptTruncatedSections;
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
