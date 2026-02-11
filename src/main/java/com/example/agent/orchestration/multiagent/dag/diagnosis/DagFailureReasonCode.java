package com.example.agent.orchestration.multiagent.dag.diagnosis;

/**
 * DAG 失败原因码。
 *
 * <p>用途：统一描述节点失败路径，支持审计统计与诊断归因。</p>
 */
public enum DagFailureReasonCode {

    /**
     * 节点执行异常。
     */
    NODE_FAILED,

    /**
     * 依赖等待超时。
     */
    DEPENDENCY_WAIT_TIMEOUT,

    /**
     * 节点重试耗尽。
     */
    RETRY_EXHAUSTED,

    /**
     * 状态机流转失败。
     */
    STATE_TRANSITION_FAILED,

    /**
     * 运行时执行超时。
     */
    DAG_RUNTIME_TIMEOUT,

    /**
     * 运行时通用异常。
     */
    DAG_RUNTIME_EXCEPTION,

    /**
     * 未知失败原因。
     */
    UNKNOWN
}

