package com.example.agent.orchestration.workflow;

import com.example.agent.orchestration.task.contract.TaskExecutionMode;
import com.example.agent.orchestration.task.contract.TaskSubmitCommand;
import com.example.agent.runtime.engine.AgentRuntime;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.RuntimeTaskRequest;
import com.example.agent.security.auth.TenantContext;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * 默认工作流路由实现。
 * <p>用途：将编排契约命令转换为运行时请求对象，并委托 AgentRuntime 执行。
 */
@Service
public class DefaultWorkflowRouter implements WorkflowRouter {

    private final AgentRuntime agentRuntime;

    public DefaultWorkflowRouter(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    @Override
    public RuntimeResult route(TaskSubmitCommand command,
                               TenantContext tenantContext,
                               String workflowId,
                               String taskId,
                               AtomicLong seqCounter) {
        RuntimeTaskRequest request = toRuntimeTaskRequest(command);
        return agentRuntime.run(request, tenantContext, workflowId, taskId, seqCounter);
    }

    /**
     * 将编排提交命令转换为运行时任务请求。
     */
    private RuntimeTaskRequest toRuntimeTaskRequest(TaskSubmitCommand command) {
        RuntimeTaskRequest request = new RuntimeTaskRequest();
        if (command == null) {
            return request;
        }
        request.setQuery(command.getQuery());
        request.setSessionId(command.getSessionId());
        request.setSkillName(command.getSkillName());
        request.setContext(command.getContext());
        request.setIdempotencyKey(command.getIdempotencyKey());
        request.setToolChoice(command.getToolChoice());
        request.setExecutionMode(toExecutionMode(command.getExecutionMode()));
        request.setWaitTimeoutMs(command.getWaitTimeoutMs());
        return request;
    }

    private RuntimeTaskRequest.ExecutionMode toExecutionMode(TaskExecutionMode mode) {
        if (mode == null) {
            return null;
        }
        return mode == TaskExecutionMode.SYNC
                ? RuntimeTaskRequest.ExecutionMode.SYNC
                : RuntimeTaskRequest.ExecutionMode.ASYNC;
    }
}
