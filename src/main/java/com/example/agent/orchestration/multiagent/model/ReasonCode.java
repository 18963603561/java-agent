package com.example.agent.orchestration.multiagent.model;

/**
 * 统一原因码枚举。
 *
 * <p>用途：统一运行失败、重试与背压原因码，确保事件、日志与审计口径一致。</p>
 */
public enum ReasonCode {

    /**
     * 节点失败。
     */
    NODE_FAILED,

    /**
     * 节点重试。
     */
    NODE_RETRY,

    /**
     * 状态迁移失败。
     */
    STATE_TRANSITION_FAILED,

    /**
     * 就绪队列达到上限。
     */
    READY_QUEUE_FULL,

    /**
     * 就绪队列退避等待。
     */
    READY_QUEUE_BACKOFF,

    /**
     * 就绪队列节流等待。
     */
    READY_QUEUE_THROTTLED,

    /**
     * 交接依赖等待超时。
     */
    HANDOFF_DEPENDENCY_TIMEOUT,

    /**
     * 监督流程失败。
     */
    HANDOFF_SUPERVISOR_FAILED,

    /**
     * 交接请求非法。
     */
    HANDOFF_INVALID_REQUEST,

    /**
     * 控制命令参数非法。
     */
    CONTROL_INVALID_ARGUMENT,

    /**
     * 控制目标不存在。
     */
    CONTROL_NOT_FOUND,

    /**
     * 控制命令不支持。
     */
    CONTROL_UNSUPPORTED,

    /**
     * 未知原因。
     */
    UNKNOWN;

    /**
     * 导出原因码。
     */
    public String code() {
        return name();
    }
}

