package com.example.agent.context;

import java.time.Instant;

/**
 * 证据统计信息，用于汇总证据规模与估算体量。
 */
public class EvidenceStats {

    /**
     * 工具调用证据数量。
     */
    private Integer toolCallsCount;

    /**
     * 记忆引用证据数量。
     */
    private Integer memoriesCount;

    /**
     * 引用证据数量。
     */
    private Integer citationsCount;

    /**
     * 证据文本体积估算值，可为空。
     */
    private Integer approxChars;

    /**
     * 统计更新时间，可为空。
     */
    private Instant updatedAt;

    public Integer getToolCallsCount() {
        return toolCallsCount;
    }

    public void setToolCallsCount(Integer toolCallsCount) {
        this.toolCallsCount = toolCallsCount;
    }

    public Integer getMemoriesCount() {
        return memoriesCount;
    }

    public void setMemoriesCount(Integer memoriesCount) {
        this.memoriesCount = memoriesCount;
    }

    public Integer getCitationsCount() {
        return citationsCount;
    }

    public void setCitationsCount(Integer citationsCount) {
        this.citationsCount = citationsCount;
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
