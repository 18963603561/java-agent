package com.example.agent.runtime.model;

import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.structured.structured.StructuredData;
import com.example.agent.runtime.structured.result.StructuredResult;
import java.util.List;

/**
 * 步骤执行结果统一对象。
 */
public class StepResult {

    /**
     * 元信息。
     */
    private StepResultMeta meta;

    /**
     * 原始结果引用。
     */
    private RawRef rawRef;

    /**
     * 原始结果快照。
     */
    private StepResultRaw raw;

    /**
     * 结构化主结果。
     */
    private StructuredResult<? extends StructuredData> result;

    /**
     * 语义摘要。
     */
    private SemanticSummary summary;

    /**
     * 引用集合。
     */
    private StepResultRefSet refs;

    /**
     * 错误集合。
     */
    private List<StepResultError> errors;

    public StepResultMeta getMeta() {
        return meta;
    }

    public void setMeta(StepResultMeta meta) {
        this.meta = meta;
    }

    public RawRef getRawRef() {
        return rawRef;
    }

    public void setRawRef(RawRef rawRef) {
        this.rawRef = rawRef;
    }

    public StepResultRaw getRaw() {
        return raw;
    }

    public void setRaw(StepResultRaw raw) {
        this.raw = raw;
    }

    public StructuredResult<? extends StructuredData> getResult() {
        return result;
    }

    public void setResult(StructuredResult<? extends StructuredData> result) {
        this.result = result;
    }

    public SemanticSummary getSummary() {
        return summary;
    }

    public void setSummary(SemanticSummary summary) {
        this.summary = summary;
    }

    public StepResultRefSet getRefs() {
        return refs;
    }

    public void setRefs(StepResultRefSet refs) {
        this.refs = refs;
    }

    public List<StepResultError> getErrors() {
        return errors;
    }

    public void setErrors(List<StepResultError> errors) {
        this.errors = errors;
    }
}
