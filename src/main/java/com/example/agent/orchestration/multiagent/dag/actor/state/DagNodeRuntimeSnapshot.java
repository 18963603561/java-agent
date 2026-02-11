package com.example.agent.orchestration.multiagent.dag.actor.state;

import java.time.Instant;

/**
 * DAG 节点运行时快照。
 *
 * <p>用途：持久化节点状态与依赖计数，支持跨实例恢复与一致性校验。</p>
 */
public class DagNodeRuntimeSnapshot {

    private String dagRunId;
    private String workflowId;
    private String nodeId;
    private String status;
    private int remainingDependencies;
    private int attempt;
    private long version;
    private Instant updatedAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRemainingDependencies() {
        return remainingDependencies;
    }

    public void setRemainingDependencies(int remainingDependencies) {
        this.remainingDependencies = remainingDependencies;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

