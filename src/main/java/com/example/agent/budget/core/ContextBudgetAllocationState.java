package com.example.agent.budget.core;

/**
 * 上下文预算分配状态。
 */
public enum ContextBudgetAllocationState {

    /**
     * 预算分配已启用。
     */
    ENABLED,

    /**
     * 预算被请求参数主动禁用。
     */
    DISABLED_BY_REQUEST,

    /**
     * 预算被系统配置禁用。
     */
    DISABLED_BY_CONFIG,

    /**
     * 预算因依赖不可用而禁用。
     */
    DISABLED_BY_DEPENDENCY
}

