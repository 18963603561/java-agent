package com.example.agent.orchestration.multiagent.dag.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DAG 运行审计主记录。
 *
 * <p>用途：记录一次 DAG 运行的整体信息，供回放与诊断模块查询。</p>
 */
public class DagRunAuditRecord {

    private String dagRunId;
    private String workflowId;
    private String tenantId;
    private String status;
    private String failurePolicy;
    private int maxFailures;
    private int maxRetriesPerNode;
    private Instant startedAt;
    private Instant completedAt;
    private List<String> failures = new ArrayList<>();

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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(String failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public int getMaxRetriesPerNode() {
        return maxRetriesPerNode;
    }

    public void setMaxRetriesPerNode(int maxRetriesPerNode) {
        this.maxRetriesPerNode = maxRetriesPerNode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<String> getFailures() {
        return failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures == null ? new ArrayList<>() : new ArrayList<>(failures);
    }
}

