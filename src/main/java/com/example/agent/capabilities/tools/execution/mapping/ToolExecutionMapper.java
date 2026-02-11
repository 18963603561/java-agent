package com.example.agent.capabilities.tools.execution.mapping;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionRequest;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionResult;
import com.example.agent.capabilities.tools.execution.model.ToolInvocationArguments;
import com.example.agent.capabilities.tools.execution.model.ToolInvocationPayload;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工具执行域映射器。
 *
 * <p>用途：集中处理执行域强类型模型与边界 Map 之间的转换逻辑。</p>
 */
@Component
public class ToolExecutionMapper {

    /**
     * 构造工具执行请求对象。
     *
     * @param taskRequest 任务请求
     * @param tenantContext 租户上下文
     * @param usageId 计量幂等标识
     * @param toolName 工具名称
     * @param taskId 任务标识
     * @return 强类型执行请求
     */
    public ToolExecutionRequest toExecutionRequest(TaskRequest taskRequest,
                                                   TenantContext tenantContext,
                                                   String usageId,
                                                   String toolName,
                                                   String taskId) {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setTaskRequest(taskRequest);
        request.setTenantContext(tenantContext);
        request.setUsageId(usageId);
        request.setToolName(toolName);
        request.setTaskId(taskId);
        return request;
    }

    /**
     * 构造工具调用参数对象。
     *
     * @param request 任务请求
     * @param overrideArguments 外部参数覆盖
     * @return 强类型调用参数
     */
    public ToolInvocationArguments toInvocationArguments(TaskRequest request,
                                                         Map<String, Object> overrideArguments) {
        Map<String, Object> values = new HashMap<>();
        if (request != null) {
            values.put(ToolFieldKeys.QUERY, request.getQuery());
            if (request.getContext() != null) {
                values.putAll(request.getContext());
            }
        }
        if (overrideArguments != null && !overrideArguments.isEmpty()) {
            values.putAll(overrideArguments);
        }
        return new ToolInvocationArguments(values);
    }

    /**
     * 构造工具调用输出对象。
     *
     * @param values 输出值
     * @return 输出对象
     */
    public ToolInvocationPayload toInvocationPayload(Map<String, Object> values) {
        return new ToolInvocationPayload(values);
    }

    /**
     * 构造工具执行结果对象。
     *
     * @param toolName 工具名称
     * @param payload 输出载荷
     * @param tokenUsage 计量信息
     * @param cacheHit 缓存命中
     * @param rawRef 原始引用
     * @param digest 摘要
     * @return 强类型执行结果
     */
    public ToolExecutionResult toExecutionResult(String toolName,
                                                 ToolInvocationPayload payload,
                                                 Map<String, Object> tokenUsage,
                                                 boolean cacheHit,
                                                 String rawRef,
                                                 String digest) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setToolName(toolName);
        result.setPayload(payload);
        result.setTokenUsage(tokenUsage);
        result.setCacheHit(cacheHit);
        result.setRawRef(rawRef);
        result.setResultDigest(digest);
        return result;
    }

    /**
     * 追加治理等待信息到执行结果。
     *
     * @param result 执行结果
     * @param waitingPayload 等待信息
     */
    public void attachGovernanceWaiting(ToolExecutionResult result, Map<String, Object> waitingPayload) {
        if (result == null || waitingPayload == null || waitingPayload.isEmpty()) {
            return;
        }
        result.setGovernanceWaiting(new HashMap<>(waitingPayload));
    }

    /**
     * 将执行结果转换为边界 Map。
     *
     * @param result 强类型执行结果
     * @return 结果映射
     */
    public Map<String, Object> toResultMap(ToolExecutionResult result) {
        return result == null ? Map.of() : result.toMap();
    }
}
