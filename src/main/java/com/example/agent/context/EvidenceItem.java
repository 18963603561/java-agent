package com.example.agent.context;

import java.time.Instant;

/**
 * 证据项。
 */
public class EvidenceItem {

    /**
     * 来源类型。
     */
    private String sourceType;

    /**
     * 来源标识。
     */
    private String sourceId;

    /**
     * 位置标识。
     */
    private String uri;

    /**
     * 标题。
     */
    private String title;

    /**
     * 摘要片段。
     */
    private String snippet;

    /**
     * 校验哈希。
     */
    private String hash;

    /**
     * 获取时间。
     */
    private Instant retrievedAt;

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }
}