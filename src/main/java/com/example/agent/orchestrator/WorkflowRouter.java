package com.example.agent.orchestrator;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * 工作流路由器，用于将任务路由到执行网关。
 */
@Service
public class WorkflowRouter {

    private final EnforcementGateway enforcementGateway;

    public WorkflowRouter(EnforcementGateway enforcementGateway) {
        this.enforcementGateway = enforcementGateway;
    }

    /**
     * 路由并执行任务。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     */
    public void route(TaskRequest request, TenantContext tenantContext, String workflowId, String taskId,
                      AtomicLong seqCounter) {
        enforcementGateway.execute(request, tenantContext, workflowId, taskId, seqCounter);
    }
}
