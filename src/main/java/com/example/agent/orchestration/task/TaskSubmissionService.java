package com.example.agent.orchestration.task;

import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.orchestration.task.contract.TaskSubmissionResult;
import com.example.agent.security.auth.TenantContext;

/**
 * 任务提交服务接口。
 */
public interface TaskSubmissionService {

    /**
     * 提交任务并返回提交结果。
     *
     * @param command 任务提交命令
     * @param tenantContext 租户上下文
     * @return 任务提交结果
     */
    TaskSubmissionResult submitTask(TaskSubmitCommand command, TenantContext tenantContext);
}

