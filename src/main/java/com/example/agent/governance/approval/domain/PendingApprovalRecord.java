package com.example.agent.governance.approval.domain;

import com.example.agent.governance.approval.ApprovalDecision;
import java.util.concurrent.CompletableFuture;

/**
 * 待审批记录。
 */
public class PendingApprovalRecord {

    private final String tenantId;
    private final String workflowId;
    private final String snapshotId;
    private final String toolName;
    private final String argsDigest;
    private final long createdAtEpochMs;
    private final CompletableFuture<ApprovalDecision> future;

    public PendingApprovalRecord(String tenantId,
                                 String workflowId,
                                 String snapshotId,
                                 String toolName,
                                 String argsDigest,
                                 long createdAtEpochMs,
                                 CompletableFuture<ApprovalDecision> future) {
        this.tenantId = tenantId;
        this.workflowId = workflowId;
        this.snapshotId = snapshotId;
        this.toolName = toolName;
        this.argsDigest = argsDigest;
        this.createdAtEpochMs = createdAtEpochMs;
        this.future = future;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgsDigest() {
        return argsDigest;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public CompletableFuture<ApprovalDecision> getFuture() {
        return future;
    }
}

