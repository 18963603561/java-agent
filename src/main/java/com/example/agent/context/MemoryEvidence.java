package com.example.agent.context;

import java.time.Instant;

/**
 * 记忆引用证据，用于描述被使用的记忆条目。
 */
public class MemoryEvidence {

    /**
     * 记忆标识。
     */
    private String memoryId;

    /**
     * 召回得分，可为空。
     */
    private Double score;

    /**
     * 过期时间，可为空。
     */
    private Instant expiresAt;

    /**
     * 摘要版本，可为空，用于对齐结构化摘要版本。
     */
    private String summaryVersion;

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }
}
