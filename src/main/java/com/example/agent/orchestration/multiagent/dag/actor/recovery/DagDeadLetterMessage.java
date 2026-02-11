package com.example.agent.orchestration.multiagent.dag.actor.recovery;

import com.example.agent.orchestration.multiagent.dag.actor.distributed.DagMessageEnvelope;
import java.time.Instant;

/**
 * DAG 死信消息记录。
 *
 * <p>用途：记录超限重试消息，支持后续定位与重放。</p>
 */
public class DagDeadLetterMessage {

    private String deadLetterId;
    private String dagRunId;
    private String workflowId;
    private String nodeId;
    private String reason;
    private int deliveryAttempt;
    private Instant firstFailedAt;
    private Instant lastFailedAt;
    private DagMessageEnvelope envelope;

    public String getDeadLetterId() {
        return deadLetterId;
    }

    public void setDeadLetterId(String deadLetterId) {
        this.deadLetterId = deadLetterId;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getDeliveryAttempt() {
        return deliveryAttempt;
    }

    public void setDeliveryAttempt(int deliveryAttempt) {
        this.deliveryAttempt = deliveryAttempt;
    }

    public Instant getFirstFailedAt() {
        return firstFailedAt;
    }

    public void setFirstFailedAt(Instant firstFailedAt) {
        this.firstFailedAt = firstFailedAt;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public void setLastFailedAt(Instant lastFailedAt) {
        this.lastFailedAt = lastFailedAt;
    }

    public DagMessageEnvelope getEnvelope() {
        return envelope;
    }

    public void setEnvelope(DagMessageEnvelope envelope) {
        this.envelope = envelope;
    }
}

