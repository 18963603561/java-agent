package com.example.agent.budget;

import com.example.agent.context.ContextPolicy;

/**
 * 上下文预算分配请求。
 */
public class ContextBudgetRequest {

    /**
     * 总令牌预算。
     */
    private Integer totalTokens;

    /**
     * 预留令牌预算。
     */
    private Integer reservedTokens;

    /**
     * 是否启用预算分配。
     */
    private boolean enabled = true;

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 任务标识。
     */
    private String taskId;

    /**
     * 上下文装配策略。
     */
    private ContextPolicy policy;

    /**
     * 上下文预算策略。
     */
    private ContextBudgetPolicy budgetPolicy;

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getReservedTokens() {
        return reservedTokens;
    }

    public void setReservedTokens(Integer reservedTokens) {
        this.reservedTokens = reservedTokens;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public ContextPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ContextPolicy policy) {
        this.policy = policy;
    }

    public ContextBudgetPolicy getBudgetPolicy() {
        return budgetPolicy;
    }

    public void setBudgetPolicy(ContextBudgetPolicy budgetPolicy) {
        this.budgetPolicy = budgetPolicy;
    }
}
