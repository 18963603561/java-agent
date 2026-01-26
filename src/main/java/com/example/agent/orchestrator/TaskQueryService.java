package com.example.agent.orchestrator;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskListResponse;
import com.example.agent.common.TaskQuery;
import com.example.agent.common.TaskStatusResponse;

/**
 * 任务查询服务接口。
 */
public interface TaskQueryService {

    /**
     * 查询任务状态。
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 任务状态
     */
    TaskStatusResponse getTask(String taskId, TenantContext tenantContext);

    /**
     * 查询任务列表。
     *
     * @param query 查询条件
     * @param tenantContext 租户上下文
     * @return 任务列表
     */
    TaskListResponse listTasks(TaskQuery query, TenantContext tenantContext);
}
