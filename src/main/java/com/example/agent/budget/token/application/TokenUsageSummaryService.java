package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageSummary;
import com.example.agent.security.auth.TenantContext;

/**
 * 预算汇总服务，负责按任务统计预算累计信息。
 */
public interface TokenUsageSummaryService {

    /**
     * 汇总预算信息。
     *
     * @param taskId 任务标识
     * @param tenantContext 租户上下文
     * @return 汇总结果
     */
    TokenUsageSummary summarize(String taskId, TenantContext tenantContext);
}
