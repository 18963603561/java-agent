package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextTrimSection;

/**
 * 上下文裁剪处理器接口，按分组执行分段裁剪。
 */
public interface ContextTrimHandler {

    /**
     * 处理器所属分组。
     *
     * @return 分组标识
     */
    ContextTrimSection section();

    /**
     * 按分段预算执行裁剪。
     *
     * @param context 裁剪上下文
     */
    void trimBySectionBudget(TrimContext context);

    /**
     * 按总预算超限执行裁剪。
     *
     * @param context 裁剪上下文
     * @param excess 超出的令牌数
     * @return 本次处理后剩余超限令牌数
     */
    int trimByTotalBudget(TrimContext context, int excess);
}
