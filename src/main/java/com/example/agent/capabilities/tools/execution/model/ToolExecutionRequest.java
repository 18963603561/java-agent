package com.example.agent.capabilities.tools.execution.model;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.security.auth.TenantContext;

/**
 * 工具执行请求对象。
 *
 * <p>用途：封装工具执行主链路的稳定输入，避免在执行域内散落多个裸参数。</p>
 */
public class ToolExecutionRequest {

    /**
     * 任务请求。
     */
    private TaskRequest taskRequest;
    /**
     * 租户上下文。
     */
    private TenantContext tenantContext;
    /**
     * 计量幂等标识。
     */
    private String usageId;
    /**
     * 工具名称。
     */
    private String toolName;
    /**
     * 任务标识。
     */
    private String taskId;

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

    public String getUsageId() {
        return usageId;
    }

    public void setUsageId(String usageId) {
        this.usageId = usageId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}

