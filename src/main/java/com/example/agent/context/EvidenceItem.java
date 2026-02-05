package com.example.agent.context;

import java.time.Instant;

/**
 * 证据项，描述单条可追溯证据的最小索引信息。
 */
public class EvidenceItem {

    /**
     * 证据标识。
     */
    private String evidenceId;

    /**
     * 证据类型。
     */
    private EvidenceType type;

    /**
     * 关联步骤标识。
     */
    private String stepId;

    /**
     * 来源信息，例如工具名或来源域名。
     */
    private String source;

    /**
     * 引用信息，例如 rawRef、memoryId、citationId。
     */
    private String ref;

    /**
     * 简要摘要，用于提示词与审计展示。
     */
    private String digest;

    /**
     * 证据写入时间。
     */
    private Instant createdAt;

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public EvidenceType getType() {
        return type;
    }

    public void setType(EvidenceType type) {
        this.type = type;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
