package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 默认预算阈值判定器，使用配置阈值执行超限判断。
 */
@Component
public class DefaultBudgetThresholdEvaluator implements BudgetThresholdEvaluator {

    @Value("${agent.budget.threshold-tokens:10000}")
    private int thresholdTokens;

    @Override
    public boolean isExceeded(TokenUsageSummary summary) {
        if (summary == null) {
            return false;
        }
        return summary.getTotalTokens() >= thresholdTokens;
    }

    /**
     * 获取预算阈值令牌数，用于预算分配参考。
     */
    @Override
    public int getThresholdTokens() {
        return thresholdTokens;
    }
}
