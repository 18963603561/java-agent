package com.example.agent.budget.token.event;

import com.example.agent.capabilities.llm.provider.ModelFallbackDecision;
import com.example.agent.security.auth.TenantContext;

/**
 * 预算事件发布器，负责阈值与模型降级事件广播。
 */
public interface BudgetEventPublisher {

    /**
     * 发布预算阈值事件。
     *
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     * @param totalTokens 累计令牌
     */
    void publishThresholdEvent(TenantContext tenantContext, String taskId, int totalTokens);

    /**
     * 发布预算背压事件。
     *
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     * @param totalTokens 当前累计令牌
     * @param thresholdTokens 阈值令牌
     */
    void publishBackpressureEvent(TenantContext tenantContext,
                                  String taskId,
                                  int totalTokens,
                                  int thresholdTokens);

    /**
     * 发布模型降级事件。
     *
     * @param tenantContext 租户上下文
     * @param decision 降级决策
     */
    void publishFallbackEvent(TenantContext tenantContext, ModelFallbackDecision decision);
}
