package com.example.agent.capabilities.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 证据包，记录一次任务执行过程中的证据索引信息。
 */
public class EvidencePack {

    /**
     * 版本号。
     */
    private String version = "v1";

    /**
     * 证据包标识。
     */
    private String packId;

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
     * 创建时间。
     */
    private Instant createdAt;

    /**
     * 证据项列表，仅追加不覆盖。
     */
    private List<EvidenceItem> evidences;

    /**
     * 证据统计。
     */
    private EvidenceStats stats;

    /**
     * 证据索引。
     */
    private EvidenceIndex index;

    /**
     * 重新计算统计信息。
     *
     * @return 计算后的统计对象
     */
    public EvidenceStats recomputeStats() {
        int toolCount = 0;
        int memoryCount = 0;
        int researchCount = 0;
        int truncationCount = 0;
        int approxChars = 0;
        if (evidences != null) {
            for (EvidenceItem item : evidences) {
                if (item == null) {
                    continue;
                }
                if (item.getType() == EvidenceType.TOOL_RESULT) {
                    toolCount++;
                } else if (item.getType() == EvidenceType.MEMORY) {
                    memoryCount++;
                } else if (item.getType() == EvidenceType.RESEARCH) {
                    researchCount++;
                } else if (item.getType() == EvidenceType.CONTEXT_TRUNCATION) {
                    truncationCount++;
                }
                approxChars += safeLength(item.getSource());
                approxChars += safeLength(item.getRef());
                approxChars += safeLength(item.getDigest());
            }
        }
        EvidenceStats computed = new EvidenceStats();
        computed.setToolCount(toolCount);
        computed.setMemoryCount(memoryCount);
        computed.setResearchCount(researchCount);
        computed.setTruncationCount(truncationCount);
        computed.setTotalCount(toolCount + memoryCount + researchCount + truncationCount);
        computed.setApproxChars(approxChars);
        computed.setUpdatedAt(Instant.now());
        this.stats = computed;
        return computed;
    }

    /**
     * 追加证据项。
     *
     * @param item 证据项
     */
    public void append(EvidenceItem item) {
        if (item == null) {
            return;
        }
        if (evidences == null) {
            evidences = new ArrayList<>();
        }
        evidences.add(item);
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

    public String getPackId() {
        return packId;
    }

    public void setPackId(String packId) {
        this.packId = packId;
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

    public List<EvidenceItem> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<EvidenceItem> evidences) {
        this.evidences = evidences;
    }

    public EvidenceStats getStats() {
        return stats;
    }

    public void setStats(EvidenceStats stats) {
        this.stats = stats;
    }

    public EvidenceIndex getIndex() {
        return index;
    }

    public void setIndex(EvidenceIndex index) {
        this.index = index;
    }
}
