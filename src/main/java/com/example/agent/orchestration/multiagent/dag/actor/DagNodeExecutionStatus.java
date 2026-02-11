package com.example.agent.orchestration.multiagent.dag.actor;

/**
 * DAG 节点执行状态。
 *
 * <p>用途：描述节点在 Actor 运行时中的生命周期阶段。</p>
 */
public enum DagNodeExecutionStatus {
    /**
     * 待处理状态。
     */
    PENDING,
    /**
     * 已就绪状态。
     */
    READY,
    /**
     * 执行中状态。
     */
    RUNNING,
    /**
     * 成功完成状态。
     */
    SUCCEEDED,
    /**
     * 失败状态。
     */
    FAILED
}

