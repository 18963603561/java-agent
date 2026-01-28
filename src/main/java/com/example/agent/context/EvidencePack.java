package com.example.agent.context;

import java.time.Instant;
import java.util.List;

/**
 * 证据包，用于记录一次流程或步骤的证据信息，支持审计与回放。
 */
public class EvidencePack {

    /**
     * 版本号，默认 v1。
     */
    private String version = "v1";

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 快照标识，可为空。
     */
    private String snapshotId;

    /**
     * 证据包创建时间。
     */
    private Instant createdAt;

    /**
     * 工具调用证据列表。
     */
    private List<ToolCallEvidence> toolCalls;

    /**
     * 记忆引用证据列表。
     */
    private List<MemoryEvidence> memoriesUsed;

    /**
     * 引用信息列表。
     */
    private List<Citation> citations;

    /**
     * 证据统计信息。
     */
    private EvidenceStats stats;

    /**
     * 旧版证据项列表，保留用于兼容历史链路。
     */
    private List<EvidenceItem> items;

    /**
     * 重新计算统计信息并写回当前证据包，空列表按 0 处理。
     *
     * @return 最新统计结果
     */
    public EvidenceStats recomputeStats() {
        EvidenceStats computed = new EvidenceStats();
        computed.setToolCallsCount(toolCalls == null ? 0 : toolCalls.size());
        computed.setMemoriesCount(memoriesUsed == null ? 0 : memoriesUsed.size());
        computed.setCitationsCount(citations == null ? 0 : citations.size());
        computed.setApproxChars(estimateApproxChars());
        computed.setUpdatedAt(Instant.now());
        this.stats = computed;
        return computed;
    }

    private int estimateApproxChars() {
        int total = 0;
        if (toolCalls != null) {
            for (ToolCallEvidence call : toolCalls) {
                if (call == null) {
                    continue;
                }
                total += safeLength(call.getToolName());
                total += safeLength(call.getArgsDigest());
                total += safeLength(call.getResultDigest());
                total += safeLength(call.getStatus());
                total += safeLength(call.getErrorCode());
                total += safeLength(call.getToolCallId());
            }
        }
        if (memoriesUsed != null) {
            for (MemoryEvidence memory : memoriesUsed) {
                if (memory == null) {
                    continue;
                }
                total += safeLength(memory.getMemoryId());
                total += safeLength(memory.getSummaryVersion());
            }
        }
        if (citations != null) {
            for (Citation citation : citations) {
                if (citation == null) {
                    continue;
                }
                total += safeLength(citation.getType());
                total += safeLength(citation.getRefId());
                total += safeLength(citation.getLabel());
            }
        }
        return total;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<ToolCallEvidence> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallEvidence> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<MemoryEvidence> getMemoriesUsed() {
        return memoriesUsed;
    }

    public void setMemoriesUsed(List<MemoryEvidence> memoriesUsed) {
        this.memoriesUsed = memoriesUsed;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }

    public EvidenceStats getStats() {
        return stats;
    }

    public void setStats(EvidenceStats stats) {
        this.stats = stats;
    }

    public List<EvidenceItem> getItems() {
        return items;
    }

    public void setItems(List<EvidenceItem> items) {
        this.items = items;
    }
}
