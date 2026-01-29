package com.example.agent.model;

import java.time.Instant;

/**
 * 模型降级决策记录。
 */
public class ModelFallbackDecision {

    /**
     * 决策标识。
     */
    private String decisionId;
    /**
     * 租户标识。
     */
    private String tenantId;
    /**
     * 任务标识。
     */
    private String taskId;
    /**
     * 原模型标识。
     */
    private String fromModel;
    /**
     * 目标模型标识。
     */
    private String toModel;
    /**
     * 降级原因说明。
     */
    private String reason;
    /**
     * 决策时间。
     */
    private Instant decidedAt;

    /**
     * 空构造方法，便于序列化。
     */
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
