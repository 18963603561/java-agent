package com.example.agent.context;

import java.time.Instant;

/**
 * 证据统计信息，用于汇总不同类型证据规模与体积估算。
 */
public class EvidenceStats {

    /**
     * 工具证据数量。
     */
    private Integer toolCount;

    /**
     * 记忆证据数量。
     */
    private Integer memoryCount;

    /**
     * 研究证据数量。
     */
    private Integer researchCount;

    /**
     * 裁剪证据数量。
     */
    private Integer truncationCount;

    /**
     * 证据总数。
     */
    private Integer totalCount;

    /**
     * 证据摘要字符数估算。
     */
    private Integer approxChars;

    /**
     * 统计更新时间。
     */
    private Instant updatedAt;

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }

    public Integer getMemoryCount() {
        return memoryCount;
    }

    public void setMemoryCount(Integer memoryCount) {
        this.memoryCount = memoryCount;
    }

    public Integer getResearchCount() {
        return researchCount;
    }

    public void setResearchCount(Integer researchCount) {
        this.researchCount = researchCount;
    }

    public Integer getTruncationCount() {
        return truncationCount;
    }

    public void setTruncationCount(Integer truncationCount) {
        this.truncationCount = truncationCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getApproxChars() {
        return approxChars;
    }

    public void setApproxChars(Integer approxChars) {
        this.approxChars = approxChars;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
