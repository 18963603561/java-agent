package com.example.agent.orchestration.multiagent.observability;

/**
 * 多智能体指标键字典。
 * <p>用途：统一管理 multiagent 子域的指标名称，避免业务类内硬编码。</p>
 */
public final class MultiAgentMetricKeys {

    /**
     * 恢复阶段检测到可补偿节点次数。
     */
    public static final String DAG_RECOVERY_DETECTED = "dag.recovery.detected";

    /**
     * 恢复流程耗时摘要。
     */
    public static final String DAG_RECOVERY_DURATION = "dag.recovery.duration";

    /**
     * 死信重放次数。
     */
    public static final String DAG_RECOVERY_DEADLETTER_REPLAYED = "dag.recovery.deadletter.replayed";

    /**
     * 控制命令失败次数。
     */
    public static final String DAG_CONTROL_FAILED = "dag.control.failed";

    /**
     * 控制命令成功次数。
     */
    public static final String DAG_CONTROL_SUCCESS = "dag.control.success";

    /**
     * 恢复运行时分发计数。
     */
    public static final String DAG_CONTROL_RESUME_DISPATCHED = "dag.control.resume.dispatched";

    /**
     * 重平衡待处理消息数。
     */
    public static final String DAG_CONTROL_REBALANCE_PENDING = "dag.control.rebalance.pending";

    /**
     * 邮箱堆积量。
     */
    public static final String DAG_MAILBOX_LAG = "dag.mailbox.lag";

    /**
     * 分发进入死信次数。
     */
    public static final String DAG_DISPATCH_DLQ = "dag.dispatch.dlq";

    /**
     * 分发退回次数。
     */
    public static final String DAG_DISPATCH_NACK = "dag.dispatch.nack";

    /**
     * 分发确认次数。
     */
    public static final String DAG_DISPATCH_ACK = "dag.dispatch.ack";

    /**
     * 分发发送次数。
     */
    public static final String DAG_DISPATCH_SENT = "dag.dispatch.sent";

    private MultiAgentMetricKeys() {
    }
}

