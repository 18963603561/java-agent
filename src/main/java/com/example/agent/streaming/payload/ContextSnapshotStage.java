package com.example.agent.streaming.payload;

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
     * 上下文压缩触发阶段。
     */
    CONTEXT_COMPRESSION_TRIGGERED,

    /**
     * 上下文压缩窗口整形阶段。
     */
    CONTEXT_COMPRESSION_SHAPED,

    /**
     * 上下文压缩执行阶段。
     */
    CONTEXT_COMPRESSION_EXECUTED,

    /**
     * 上下文压缩摘要注入阶段。
     */
    CONTEXT_COMPRESSION_INJECTED,

    /**
     * 上下文压缩跳过阶段。
     */
    CONTEXT_COMPRESSION_SKIPPED,

    /**
     * 上下文压缩失败阶段。
     */
    CONTEXT_COMPRESSION_FAILED,

    /**
     * 上下文压缩完成。
     */
    CONTEXT_COMPRESSED

    ;

    /**
     * 判断是否为压缩阶段。
     *
     * @return true 表示压缩阶段，false 表示非压缩阶段
     */
    public boolean isCompressionStage() {
        // 阶段判断：根据枚举值范围识别压缩链路阶段，便于统一发布压缩事件。
        return this == CONTEXT_COMPRESSION_TRIGGERED
                || this == CONTEXT_COMPRESSION_SHAPED
                || this == CONTEXT_COMPRESSION_EXECUTED
                || this == CONTEXT_COMPRESSION_INJECTED
                || this == CONTEXT_COMPRESSION_SKIPPED
                || this == CONTEXT_COMPRESSION_FAILED
                || this == CONTEXT_COMPRESSED;
    }
}
