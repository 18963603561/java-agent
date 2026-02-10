package com.example.agent.orchestration.workflow;

import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.auth.TenantContext;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工作流路由器。
 * <p>用途：屏蔽编排层与运行时之间的调用差异，统一在编排域接收提交命令。
 */
public interface WorkflowRouter {

    /**
     * 路由并执行任务。
     *
     * @param command 任务提交命令
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     * @return 运行结果
     */
    RuntimeResult route(TaskSubmitCommand command,
                        TenantContext tenantContext,
                        String workflowId,
                        String taskId,
                        AtomicLong seqCounter);

}
