package com.example.agent.orchestration.workflow;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.engine.AgentRuntime;
import com.example.agent.runtime.engine.RuntimeResult;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * 工作流路由器，用于将任务路由到执行入口。
 * <p>用途：统一路由入口，隔离控制层与运行时细节。
 * <p>输入：任务请求、租户上下文与工作流标识。
 * <p>输出：运行时结果对象。
 * <p>边界：运行时异常将透传给上层处理。
 * <p>示例：
 * <pre>{@code
 * RuntimeResult result = workflowRouter.route(request, tenantContext, workflowId, taskId, seqCounter);
 * }</pre>
 */
@Service
public class WorkflowRouter {

    /**
     * 运行时执行器。
     * <p>示例：调用 {@code AgentRuntime.run} 执行任务。
     */
    private final AgentRuntime agentRuntime;

    /**
     * 构造工作流路由器。
     *
     * @param agentRuntime 运行时执行器
     */
    public WorkflowRouter(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    /**
     * 路由并执行任务。
     *
     * <p>输入：任务请求、租户上下文、工作流标识与任务标识。
     * <p>输出：运行时执行结果。
     * <p>边界：运行时抛出异常时不做拦截。
     * <p>示例：
     * <pre>{@code
     * RuntimeResult result = route(request, tenantContext, workflowId, taskId, seqCounter);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     */
    public RuntimeResult route(TaskRequest request,
                               TenantContext tenantContext,
                               String workflowId,
                               String taskId,
                               AtomicLong seqCounter) {
        // 直接交由运行时执行，不在路由层做业务处理。
        return agentRuntime.run(request, tenantContext, workflowId, taskId, seqCounter);
    }
}
