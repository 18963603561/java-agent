package com.example.agent.orchestration.multiagent.dag.actor;

/**
 * DAG 消息投递结果。
 *
 * <p>用途：表达邮箱投递是否成功以及失败原因，供运行时治理逻辑消费。</p>
 */
public class DagMessageDeliveryResult {

    private final boolean accepted;
    private final String reason;
    private final int queueSize;
    private final int capacity;

    private DagMessageDeliveryResult(boolean accepted, String reason, int queueSize, int capacity) {
        this.accepted = accepted;
        this.reason = reason;
        this.queueSize = queueSize;
        this.capacity = capacity;
    }

    public static DagMessageDeliveryResult accepted(int queueSize, int capacity) {
        return new DagMessageDeliveryResult(true, null, queueSize, capacity);
    }

    public static DagMessageDeliveryResult rejected(String reason, int queueSize, int capacity) {
        return new DagMessageDeliveryResult(false, reason, queueSize, capacity);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getCapacity() {
        return capacity;
    }
}

