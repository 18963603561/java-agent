package com.example.agent.streaming;

import com.example.agent.context.AuditMetadata;
import com.example.agent.context.BudgetState;
import com.example.agent.context.BuildMetrics;
import com.example.agent.context.ToolState;
import java.time.Instant;

/**
 * 上下文事件载荷，描述快照摘要、预算与裁剪信息。
 */
public class ContextEventPayload {

    /**
     * 事件标识。
     */
    private String eventId;

    /**
     * 事件类型。
     */
    private String eventType;

    /**
     * 快照标识。
     */
    private String snapshotId;

    /**
     * 调用链路追踪标识。
     */
    private String traceId;

    /**
     * 请求标识。
     */
    private String requestId;

    /**
     * 变更摘要。
     */
    private ContextDelta delta;

    /**
     * 快照统计摘要。
     */
    private ContextSnapshotSummary snapshotSummary;

    /**
     * 预算统计摘要。
     */
    private ContextBudgetSummary budgetSummary;

    /**
     * 裁剪统计摘要。
     */
    private ContextPruneSummary pruneSummary;

    /**
     * 预算状态。
     */
    private BudgetState budgetState;

    /**
     * 工具状态。
     */
    private ToolState toolState;

    /**
     * 构建指标。
     */
    private BuildMetrics buildMetrics;

    /**
     * 审计元数据。
     */
    private AuditMetadata auditMetadata;

    /**
     * 证据包版本，可为空。
     */
    private String evidencePackVersion;

    /**
     * 工具调用证据数量，可为空。
     */
    private Integer evidenceToolCallsCount;

    /**
     * 记忆引用证据数量，可为空。
     */
    private Integer evidenceMemoriesCount;

    /**
     * 引用证据数量，可为空。
     */
    private Integer evidenceCitationsCount;

    /**
     * 证据体积估算字符数，可为空。
     */
    private Integer evidenceApproxChars;

    /**
     * 证据包是否存在，可为空。
     */
    private Boolean evidencePackPresent;

    /**
     * 事件时间。
     */
    private Instant eventTime;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public ContextDelta getDelta() {
        return delta;
    }

    public void setDelta(ContextDelta delta) {
        this.delta = delta;
    }

    public ContextSnapshotSummary getSnapshotSummary() {
        return snapshotSummary;
    }

    public void setSnapshotSummary(ContextSnapshotSummary snapshotSummary) {
        this.snapshotSummary = snapshotSummary;
    }

    public ContextBudgetSummary getBudgetSummary() {
        return budgetSummary;
    }

    public void setBudgetSummary(ContextBudgetSummary budgetSummary) {
        this.budgetSummary = budgetSummary;
    }

    public ContextPruneSummary getPruneSummary() {
        return pruneSummary;
    }

    public void setPruneSummary(ContextPruneSummary pruneSummary) {
        this.pruneSummary = pruneSummary;
    }

    public BudgetState getBudgetState() {
        return budgetState;
    }

    public void setBudgetState(BudgetState budgetState) {
        this.budgetState = budgetState;
    }

    public ToolState getToolState() {
        return toolState;
    }

    public void setToolState(ToolState toolState) {
        this.toolState = toolState;
    }

    public BuildMetrics getBuildMetrics() {
        return buildMetrics;
    }

    public void setBuildMetrics(BuildMetrics buildMetrics) {
        this.buildMetrics = buildMetrics;
    }

    public AuditMetadata getAuditMetadata() {
        return auditMetadata;
    }

    public void setAuditMetadata(AuditMetadata auditMetadata) {
        this.auditMetadata = auditMetadata;
    }

    public String getEvidencePackVersion() {
        return evidencePackVersion;
    }

    public void setEvidencePackVersion(String evidencePackVersion) {
        this.evidencePackVersion = evidencePackVersion;
    }

    public Integer getEvidenceToolCallsCount() {
        return evidenceToolCallsCount;
    }

    public void setEvidenceToolCallsCount(Integer evidenceToolCallsCount) {
        this.evidenceToolCallsCount = evidenceToolCallsCount;
    }

    public Integer getEvidenceMemoriesCount() {
        return evidenceMemoriesCount;
    }

    public void setEvidenceMemoriesCount(Integer evidenceMemoriesCount) {
        this.evidenceMemoriesCount = evidenceMemoriesCount;
    }

    public Integer getEvidenceCitationsCount() {
        return evidenceCitationsCount;
    }

    public void setEvidenceCitationsCount(Integer evidenceCitationsCount) {
        this.evidenceCitationsCount = evidenceCitationsCount;
    }

    public Integer getEvidenceApproxChars() {
        return evidenceApproxChars;
    }

    public void setEvidenceApproxChars(Integer evidenceApproxChars) {
        this.evidenceApproxChars = evidenceApproxChars;
    }

    public Boolean getEvidencePackPresent() {
        return evidencePackPresent;
    }

    public void setEvidencePackPresent(Boolean evidencePackPresent) {
        this.evidencePackPresent = evidencePackPresent;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }
}
