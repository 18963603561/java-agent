package com.example.agent.streaming;

/**
 * 上下文快照阶段枚举，用于标识关键事件节点。
 */
public enum ContextSnapshotStage {

    /**
     * 规划提示词裁剪与装配完成。
     */
    PLAN_ASSEMBLED,

    /**
     * 工具观察结果写入后。
     */
    TOOL_OBSERVED,

    /**
     * 上下文裁剪完成。
     */
    CONTEXT_TRIMMED,

    /**
     * 上下文压缩完成。
     */
    CONTEXT_COMPRESSED
}
