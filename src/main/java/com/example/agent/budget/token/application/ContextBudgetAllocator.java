package com.example.agent.budget.token.application;

import com.example.agent.budget.core.ContextBudgetAllocation;

/**
 * 上下文预算分配器接口。
 */
public interface ContextBudgetAllocator {

    /**
     * 分配上下文预算。
     *
     * @param request 预算请求
     * @return 分配结果
     */
    ContextBudgetAllocation allocate(ContextBudgetRequest request);
}
