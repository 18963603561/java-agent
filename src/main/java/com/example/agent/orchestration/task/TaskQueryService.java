package com.example.agent.orchestration.task;

import com.example.agent.orchestration.task.contract.TaskListView;
import com.example.agent.orchestration.task.contract.TaskQueryCommand;
import com.example.agent.orchestration.task.contract.TaskStatusView;
import com.example.agent.security.auth.TenantContext;

/**
 * 任务查询服务接口。
 */
public interface TaskQueryService {

    /**
     * 查询任务状态。
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 任务状态视图
     */
    TaskStatusView getTask(String taskId, TenantContext tenantContext);

    /**
     * 查询任务列表。
     *
     * @param command 查询命令
     * @param tenantContext 租户上下文
     * @return 任务列表视图
     */
    TaskListView listTasks(TaskQueryCommand command, TenantContext tenantContext);
}

