package com.example.agent.runtime.structured;

import java.util.Map;

/**
 * 结构化结果统一外壳。
 */
public class StructuredResult {

    /**
     * 结果类型。
     */
    private ResultKind kind;

    /**
     * 数据结构版本。
     */
    private Integer schemaVersion;

    /**
     * 类型专属数据。
     */
    private Map<String, Object> data;

    /**
     * 质量信息。
     */
    private StructuredQuality quality;

    /**
     * 引用信息。
     */
    private StructuredRefs refs;

    public ResultKind getKind() {
        return kind;
    }

    public void setKind(ResultKind kind) {
        this.kind = kind;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public StructuredQuality getQuality() {
        return quality;
    }

    public void setQuality(StructuredQuality quality) {
        this.quality = quality;
    }

    public StructuredRefs getRefs() {
        return refs;
    }

    public void setRefs(StructuredRefs refs) {
        this.refs = refs;
    }
}
