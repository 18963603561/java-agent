package com.example.agent.runtime.raw.output;

import java.util.Map;

/**
 * 原始输出封装对象。
 *
 * <p>用途：为步骤输出提供受控的原始层结构，避免在主链路中以 {@code Map} 约定的方式透传。
 * <p>输入：步骤原始输出映射（由执行器或外部工具返回）。
 * <p>输出：包含 rawRef、refs、裁剪后的 data 与截断标记 truncated 的封装对象。
 * <p>边界：当原始层未启用或输入为空时，{@code data} 可能为空。
 */
public final class RawOutputEnvelope {

    /**
     * 原始输出引用键。
     */
    private final String rawRef;

    /**
     * 引用集合（仅字符串键值对）。
     */
    private final Map<String, String> refs;

    /**
     * 裁剪后的原始数据。
     *
     * <p>注意：该数据用于审计与回放，不应直接注入提示词。</p>
     */
    private final Map<String, Object> data;

    /**
     * 是否发生裁剪。
     */
    private final boolean truncated;

    private RawOutputEnvelope(String rawRef,
                              Map<String, String> refs,
                              Map<String, Object> data,
                              boolean truncated) {
        this.rawRef = rawRef;
        this.refs = refs == null ? Map.of() : refs;
        this.data = data;
        this.truncated = truncated;
    }

    /**
     * 构造空封装。
     */
    public static RawOutputEnvelope empty() {
        return new RawOutputEnvelope(null, Map.of(), null, false);
    }

    /**
     * 构造封装对象。
     *
     * @param rawRef 原始引用键
     * @param refs 引用集合
     * @param data 裁剪后的原始数据
     * @param truncated 是否截断
     * @return 原始输出封装
     */
    public static RawOutputEnvelope of(String rawRef,
                                       Map<String, String> refs,
                                       Map<String, Object> data,
                                       boolean truncated) {
        return new RawOutputEnvelope(rawRef, refs, data, truncated);
    }

    public String getRawRef() {
        return rawRef;
    }

    public Map<String, String> getRefs() {
        return refs;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public boolean isTruncated() {
        return truncated;
    }
}

