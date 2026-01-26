package com.example.agent.model;

import java.time.Instant;

/**
 * 模型降级决策记录。
 */
public class ModelFallbackDecision {

    private String decisionId;
    private String tenantId;
    private String taskId;
    private String fromModel;
    private String toModel;
    private String reason;
    private Instant decidedAt;

    public ModelFallbackDecision() {
    }

    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getFromModel() {
        return fromModel;
    }

    public void setFromModel(String fromModel) {
        this.fromModel = fromModel;
    }

    public String getToModel() {
        return toModel;
    }

    public void setToModel(String toModel) {
        this.toModel = toModel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
