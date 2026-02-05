package com.example.agent.context;

/**
 * 证据类型枚举，用于统一证据分类。
 */
public enum EvidenceType {
    /**
     * 工具调用结果证据。
     */
    TOOL_RESULT,
    /**
     * 记忆召回证据。
     */
    MEMORY,
    /**
     * 研究引用证据。
     */
    RESEARCH,
    /**
     * 上下文裁剪证据。
     */
    CONTEXT_TRUNCATION
}
