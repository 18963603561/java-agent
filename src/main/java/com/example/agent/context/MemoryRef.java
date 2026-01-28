package com.example.agent.context;

import java.time.Instant;

/**
 * 记忆引用信息。
 */
public class MemoryRef {

    /**
     * 记忆标识。
     */
    private String memoryId;

    /**
     * 记忆类型。
     */
    private String memoryType;

    /**
     * 匹配分值。
     */
    private Double score;

    /**
     * 摘要片段。
     */
    private String snippet;

    /**
     * 过期时间。
     */
    private Instant expiresAt;

    /**
     * 来源标识。
     */
    private String source;

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}