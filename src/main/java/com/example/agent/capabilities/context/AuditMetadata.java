package com.example.agent.capabilities.context;

import java.time.Instant;

/**
 * 审计元数据。
 */
public class AuditMetadata {

    /**
     * 结构版本。
     */
    private String version;

    /**
     * 数据来源。
     */
    private String source;

    /**
     * 创建时间。
     */
    private Instant createdAt;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}