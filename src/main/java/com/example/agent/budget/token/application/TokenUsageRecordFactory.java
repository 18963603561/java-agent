package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.security.auth.TenantContext;

/**
 * 预算记录工厂，负责从输入构建标准化记录对象。
 */
public interface TokenUsageRecordFactory {

    /**
     * 构建预算记录。
     *
     * @param input 计量输入
     * @param tenantContext 租户上下文
     * @return 预算记录
     */
    TokenUsageRecord create(TokenUsageInput input, TenantContext tenantContext);
}
