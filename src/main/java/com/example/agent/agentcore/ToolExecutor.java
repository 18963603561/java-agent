package com.example.agent.agentcore;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.TokenBudgetManager;
import com.example.agent.budget.TokenUsageInput;
import com.example.agent.budget.TokenUsageRecord;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelDefinition;
import com.example.agent.model.ModelRouter;
import com.example.agent.model.ModelScene;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 工具执行器，负责工具调用与结果汇总。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final ToolCache toolCache;
    private final SandboxExecutor sandboxExecutor;
    private final TokenBudgetManager tokenBudgetManager;
    private final ModelRouter modelRouter;

    public ToolExecutor(ToolRegistry toolRegistry,
                        ToolCache toolCache,
                        SandboxExecutor sandboxExecutor,
                        TokenBudgetManager tokenBudgetManager,
                        ModelRouter modelRouter) {
        this.toolRegistry = toolRegistry;
        this.toolCache = toolCache;
        this.sandboxExecutor = sandboxExecutor;
        this.tokenBudgetManager = tokenBudgetManager;
        this.modelRouter = modelRouter;
    }

    /**
     * 执行工具调用并返回结果。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param usageId 计量幂等键
     * @param toolName 工具名称
     * @return 执行结果
     */
    public Map<String, Object> execute(TaskRequest request,
                                       TenantContext tenantContext,
                                       String usageId,
                                       String toolName,
                                       String taskId) {
        String resolvedTool = toolRegistry.resolve(toolName);
        log.info("ToolExecutor invoking tool, tenantId={}, tool={}, usageId={}",
                tenantContext.getTenantId(), resolvedTool, usageId);
        toolCache.put(resolvedTool, "cached");

        Map<String, Object> arguments = buildArguments(request);
        Map<String, Object> output = sandboxExecutor.execute(resolvedTool, request, tenantContext, arguments);
        Map<String, Object> toolResult = toolRegistry.execute(resolvedTool, arguments);
        Map<String, Object> merged = new HashMap<>(toolResult);
        merged.putAll(output == null ? Map.of() : output);

        TokenUsageRecord usageRecord = recordUsage(tenantContext, request, usageId, resolvedTool, merged, taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("tool", resolvedTool);
        result.put("result", merged);
        result.put("tokenUsage", usageRecord);
        return result;
    }

    private Map<String, Object> buildArguments(TaskRequest request) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", request.getQuery());
        if (request.getContext() != null) {
            arguments.putAll(request.getContext());
        }
        return arguments;
    }

    private TokenUsageRecord recordUsage(TenantContext tenantContext,
                                         TaskRequest request,
                                         String usageId,
                                         String toolName,
                                         Map<String, Object> output,
                                         String taskId) {
        int inputTokens = request.getQuery() != null ? request.getQuery().length() : 0;
        int outputTokens = output != null ? output.toString().length() : 0;
        ModelDefinition model = modelRouter.route(ModelScene.CHEAP);
        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId(usageId);
        input.setTenantId(tenantContext.getTenantId());
        input.setTaskId(taskId);
        input.setAgentId(toolName);
        input.setModel(model != null ? model.getModelId() : "default");
        input.setProvider(model != null ? model.getProvider() : "local");
        input.setInputTokens(inputTokens);
        input.setOutputTokens(outputTokens);
        input.setTotalTokens(inputTokens + outputTokens);
        TokenUsageRecord record = tokenBudgetManager.recordUsage(input, tenantContext);
        log.info("预算计量完成, tenantId={}, usageId={}, totalTokens={}",
                tenantContext.getTenantId(), usageId, record.getTotalTokens());
        return record;
    }
}
