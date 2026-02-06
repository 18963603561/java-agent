package com.example.agent.runtime.structured;

import java.util.Map;

/**
 * 结构化结果统一外壳。
 * StructuredResult 表示步骤执行后产生的“结构化语义结果”统一外壳。
 *
 * 设计目标：
 * 1. 将不同工具 / 模型输出进行语义归一化；
 * 2. 为 Runtime / Planner / UI 提供稳定的消费契约；
 * 3. 支持版本演进、质量评估与引用追踪；
 * 4. 避免业务代码直接依赖原始 raw 输出结构。
 *
 * 设计原则：
 * - StructuredResult 不保存原始数据（raw），仅保存语义化结果；
 * - 所有结构映射必须经过 StructuredExtractorRegistry 统一收口；
 * - 允许不同 kind 对应不同 data 结构；
 * - 必须保证跨版本向前兼容。
 */
public class StructuredResult {

    /**
     * 结果语义类型。
     *
     * 用于标识 data 的业务语义类别，
     * 例如：SQL、RECORDSET、ENTITY_LIST 等。
     *
     * 消费方通常会根据 kind 决定后续处理策略。
     */
    private ResultKind kind;

    /**
     * 数据结构版本号。
     *
     * 用于支持结构演进（向后兼容）。
     * 当 data 结构发生变化时递增。
     *
     * 例如：
     * v1: {"rows": [...]}
     * v2: {"columns": [...], "rows": [...]}
     */
    private Integer schemaVersion;

    /**
     * 类型专属语义数据。
     *
     * 注意：
     * - 结构必须与 kind 对应；
     * - 由 StructuredExtractor 统一生成；
     * - 不应包含原始大文本；
     * - 仅包含“可依赖的稳定结构”。
     *
     * 示例：
     * SQL:
     *   {"sql": "...", "dialect": "fmdb"}
     *
     * RECORDSET:
     *   {"columns": [...], "rows": [...]}
     */
    private Map<String, Object> data;

    /**
     * 结构化结果质量信息。
     *
     * 用于表达：
     * - 置信度
     * - 是否完整
     * - 是否截断
     * - 是否存在字段缺失
     *
     * 主要用于：
     * - 重试决策
     * - 质量评分
     * - 反思步骤（Reflection）
     */
    private StructuredQuality quality;

    /**
     * 结构化引用集合。
     *
     * 用于记录：
     * - 数据来源
     * - 文档片段引用
     * - 工具调用来源
     *
     * 该信息将进入 EvidencePack，
     * 用于审计与可追溯性分析。
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

