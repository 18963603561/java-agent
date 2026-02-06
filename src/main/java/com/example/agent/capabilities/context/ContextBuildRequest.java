package com.example.agent.capabilities.context;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.ContextBudgetRequest;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.MemoryRecallResult;
import com.example.agent.runtime.react.ReactObservation;
import com.example.agent.capabilities.tools.ToolQuery;
import java.util.List;
import java.util.Map;

/**
 * 上下文构建请求。
 */
public class ContextBuildRequest {

    /**
     * 任务请求。
     */
    private TaskRequest taskRequest;

    /**
     * 租户上下文。
     */
    private TenantContext tenantContext;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 任务标识。
     */
    private String taskId;

    /**
     * 记忆召回结果。
     */
    private MemoryRecallResult recallResult;

    /**
     * 观察记录列表。
     */
    private List<ReactObservation> observations;

    /**
     * 运行时上下文。
     */
    private Map<String, Object> runtimeContext;

    /**
     * 上下文装配策略。
     */
    private ContextPolicy policy;

    /**
     * 工具查询条件。
     */
    private ToolQuery toolQuery;

    /**
     * 预算分配请求。
     */
    private ContextBudgetRequest budgetRequest;

    public TaskRequest getTaskRequest() {
        return taskRequest;
    }

    public void setTaskRequest(TaskRequest taskRequest) {
        this.taskRequest = taskRequest;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public void setTenantContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
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

    public MemoryRecallResult getRecallResult() {
        return recallResult;
    }

    public void setRecallResult(MemoryRecallResult recallResult) {
        this.recallResult = recallResult;
    }

    public List<ReactObservation> getObservations() {
        return observations;
    }

    public void setObservations(List<ReactObservation> observations) {
        this.observations = observations;
    }

    public Map<String, Object> getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(Map<String, Object> runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public ContextPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ContextPolicy policy) {
        this.policy = policy;
    }

    public ToolQuery getToolQuery() {
        return toolQuery;
    }

    public void setToolQuery(ToolQuery toolQuery) {
        this.toolQuery = toolQuery;
    }

    public ContextBudgetRequest getBudgetRequest() {
        return budgetRequest;
    }

    public void setBudgetRequest(ContextBudgetRequest budgetRequest) {
        this.budgetRequest = budgetRequest;
    }
}
