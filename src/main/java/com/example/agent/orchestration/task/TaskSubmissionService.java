package com.example.agent.orchestration.task;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.api.http.dto.TaskResponse;

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
