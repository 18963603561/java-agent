package com.example.agent.capabilities.context.model;

import java.time.Instant;

/**
 * 引用信息，用于记录来源与证据关联信息。
 */
public class Citation {

    /**
     * 引用类型，可为空。
     */
    private String type;

    /**
     * 引用标识，可为空。
     */
    private String refId;

    /**
     * 引用标签，可为空。
     */
    private String label;

    /**
     * 来源标识，可为空。
     */
    private String source;

    /**
     * 标题，可为空。
     */
    private String title;

    /**
     * 位置标识，可为空。
     */
    private String uri;

    /**
     * 摘要片段，可为空。
     */
    private String snippet;

    /**
     * 获取时间，可为空。
     */
    private Instant fetchedAt;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
