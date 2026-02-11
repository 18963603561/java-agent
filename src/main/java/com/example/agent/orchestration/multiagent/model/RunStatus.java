package com.example.agent.orchestration.multiagent.model;

/**
 * 统一运行状态枚举。
 *
 * <p>用途：统一描述多智能体与控制面状态，避免字符串散落导致口径不一致。</p>
 */
public enum RunStatus {

    /**
     * 运行中。
     */
    RUNNING,

    /**
     * 已暂停。
     */
    PAUSED,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 部分成功。
     */
    PARTIAL_SUCCESS,

    /**
     * 已失败。
     */
    FAILED,

    /**
     * 依赖等待中。
     */
    WAITING_DEPENDENCIES,

    /**
     * DAG 节点失败。
     */
    DAG_NODE_FAILED,

    /**
     * 控制面事件。
     */
    DAG_CONTROL,

    /**
     * 控制指令成功。
     */
    SUCCESS,

    /**
     * 参数非法。
     */
    INVALID_ARGUMENT,

    /**
     * 不支持的命令。
     */
    UNSUPPORTED,

    /**
     * 通用错误。
     */
    ERROR,

    /**
     * 目标不存在。
     */
    NOT_FOUND;

    /**
     * 导出状态码。
     */
    public String code() {
        return name();
    }
}

