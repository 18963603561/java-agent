package com.example.agent.budget.core;

/**
 * 上下文分段枚举。
 */
public enum ContextSection {
    /**
     * 系统策略分段。
     */
    SYSTEM_POLICY,
    /**
     * 开发者策略分段。
     */
    DEVELOPER_POLICY,
    /**
     * 任务意图与输入分段。
     */
    USER_INPUT,
    /**
     * 工作记忆分段。
     */
    WORKING_MEMORY,
    /**
     * 领域知识引用分段。
     */
    DOMAIN_KNOWLEDGE,
    /**
     * 长期记忆引用分段。
     */
    LONG_TERM_MEMORY,
    /**
     * 工具摘要分段。
     */
    TOOL_SUMMARY,
    /**
     * 工具模式分段。
     */
    TOOL_SCHEMA,
    /**
     * 证据包分段。
     */
    EVIDENCE_PACK,
    /**
     * 余量分段，用于承接剩余预算。
     */
    SLACK
}
