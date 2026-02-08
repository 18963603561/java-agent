package com.example.agent.capabilities.tools.execution.service;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.budget.token.TokenBudgetManager;
import com.example.agent.budget.token.TokenUsageInput;
import com.example.agent.budget.token.TokenUsageRecord;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * 工具执行计量记录服务。
 *
 * <p>用途：封装 token 计量记录逻辑，降低执行器对计量细节的耦合。</p>
 */
@Component
public class ToolUsageRecorder {

    /**
     * 记录工具调用计量。
     *
     * @param tokenBudgetManager 计量管理器
     * @param modelRouter 模型路由器
     * @param logger 日志器
     * @param tenantContext 租户上下文
     * @param request 任务请求
     * @param usageId 计量幂等标识
     * @param toolName 工具名称
     * @param output 输出结果
     * @param taskId 任务标识
     * @param cacheHit 是否缓存命中
     * @return 计量记录
     */
    public TokenUsageRecord record(TokenBudgetManager tokenBudgetManager,
                                   ModelRouter modelRouter,
                                   Logger logger,
                                   TenantContext tenantContext,
                                   TaskRequest request,
                                   String usageId,
                                   String toolName,
                                   Map<String, Object> output,
                                   String taskId,
                                   boolean cacheHit) {
        ModelDefinition model = modelRouter.route(ModelScene.CHEAP);
        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId(usageId);
        input.setTenantId(tenantContext.getTenantId());
        input.setTaskId(taskId);
        input.setAgentId(toolName);
        input.setModel(model != null ? model.getModelId() : "default");
        input.setProvider(model != null ? model.getProvider() : "local");
        int inputTokens = cacheHit ? 0 : (request != null && request.getQuery() != null ? request.getQuery().length() : 0);
        int outputTokens = cacheHit ? 0 : (output != null ? output.toString().length() : 0);
        input.setInputTokens(inputTokens);
        input.setOutputTokens(outputTokens);
        input.setTotalTokens(inputTokens + outputTokens);
        TokenUsageRecord record = tokenBudgetManager.recordUsage(input, tenantContext);
        logger.info("棰勭畻璁￠噺瀹屾垚, tenantId={}, usageId={}, totalTokens={}, cacheHit={}",
                tenantContext.getTenantId(), usageId, record.getTotalTokens(), cacheHit);
        return record;
    }
}

