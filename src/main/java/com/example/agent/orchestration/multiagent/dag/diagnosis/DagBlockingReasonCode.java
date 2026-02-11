package com.example.agent.orchestration.multiagent.dag.diagnosis;

/**
 * DAG 阻塞原因码。
 *
 * <p>用途：统一描述等待与背压路径，支撑阻塞热点分析。</p>
 */
public enum DagBlockingReasonCode {

    /**
     * 邮箱拥塞导致等待。
     */
    MAILBOX_OVERLOADED,

    /**
     * 就绪队列达到上限。
     */
    READY_QUEUE_FULL,

    /**
     * 就绪队列节流等待。
     */
    READY_QUEUE_THROTTLED,

    /**
     * 依赖尚未满足。
     */
    DEPENDENCY_PENDING,

    /**
     * 未知阻塞原因。
     */
    UNKNOWN
}

