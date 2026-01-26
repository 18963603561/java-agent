package com.example.agent.orchestrator;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;

/**
 * 任务提交服务接口。
 */
public interface TaskSubmissionService {

    /**
     * 提交任务并返回任务响应。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @return 任务响应
     */
    TaskResponse submitTask(TaskRequest request, TenantContext tenantContext);
}
