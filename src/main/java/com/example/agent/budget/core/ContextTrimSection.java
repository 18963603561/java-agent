package com.example.agent.budget.core;

import java.util.List;

/**
 * 上下文裁剪分组枚举，用于定义裁剪顺序。
 */
public enum ContextTrimSection {
    /**
     * 证据包与工具细节。
     */
    EVIDENCE_PACK,
    /**
     * 召回记忆与领域引用。
     */
    RECALLED_MEMORIES,
    /**
     * 工作记忆内容。
     */
    WORKING_MEMORY,
    /**
     * 工具摘要信息。
     */
    TOOL_SUMMARY,
    /**
     * 任务意图与系统/开发者策略。
     */
    TASK_AND_SYSTEM;

    /**
     * 获取默认裁剪顺序。
     *
     * @return 默认裁剪顺序列表
     */
    public static List<ContextTrimSection> defaultOrder() {
        return List.of(
                EVIDENCE_PACK,
                RECALLED_MEMORIES,
                WORKING_MEMORY,
                TOOL_SUMMARY,
                TASK_AND_SYSTEM
        );
    }
}
