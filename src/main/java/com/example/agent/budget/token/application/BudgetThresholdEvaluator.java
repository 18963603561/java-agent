package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageSummary;

/**
 * 预算阈值判定器，负责判断是否触发预算超限行为。
 */
public interface BudgetThresholdEvaluator {

    /**
     * 判断汇总结果是否达到阈值。
     *
     * @param summary 汇总结果
     * @return 是否超阈值
     */
    boolean isExceeded(TokenUsageSummary summary);

    /**
     * 获取预算阈值令牌数。
     *
     * @return 阈值令牌数
     */
    int getThresholdTokens();
}
