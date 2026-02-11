package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.dag.actor.DagMessage;
import java.time.Instant;

/**
 * DAG 分布式消息信封。
 *
 * <p>用途：为跨进程传输补充路由、重试、租约与确认字段。</p>
 */
public class DagMessageEnvelope {

    private String envelopeId;
    private String dagRunId;
    private String workflowId;
    private String nodeId;
    private String shardKey;
    private String messageId;
    private String ownerInstanceId;
    private long availableAtEpochMs;
    private int deliveryAttempt;
    private long ackDeadlineEpochMs;
    private Instant createdAt;
    private DagMessage message;

    public String getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }

    public String getDagRunId() {
        return dagRunId;
    }

    public void setDagRunId(String dagRunId) {
        this.dagRunId = dagRunId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getShardKey() {
        return shardKey;
    }

    public void setShardKey(String shardKey) {
        this.shardKey = shardKey;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getOwnerInstanceId() {
        return ownerInstanceId;
    }

    public void setOwnerInstanceId(String ownerInstanceId) {
        this.ownerInstanceId = ownerInstanceId;
    }

    public long getAvailableAtEpochMs() {
        return availableAtEpochMs;
    }

    public void setAvailableAtEpochMs(long availableAtEpochMs) {
        this.availableAtEpochMs = availableAtEpochMs;
    }

    public int getDeliveryAttempt() {
        return deliveryAttempt;
    }

    public void setDeliveryAttempt(int deliveryAttempt) {
        this.deliveryAttempt = deliveryAttempt;
    }

    public long getAckDeadlineEpochMs() {
        return ackDeadlineEpochMs;
    }

    public void setAckDeadlineEpochMs(long ackDeadlineEpochMs) {
        this.ackDeadlineEpochMs = ackDeadlineEpochMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public DagMessage getMessage() {
        return message;
    }

    public void setMessage(DagMessage message) {
        this.message = message;
    }
}

