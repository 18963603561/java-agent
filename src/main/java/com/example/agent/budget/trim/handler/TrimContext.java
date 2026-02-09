package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.ContextTrimStats;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import java.util.Map;

/**
 * 裁剪执行上下文，承载当前快照、预算与统计状态。
 */
public class TrimContext {

    private final ContextSnapshot snapshot;
    private final Map<ContextSection, Integer> budgets;
    private final Map<ContextSection, ContextTrimStats> removedBySection;
    private final ContextTokenEstimator estimator;
    private Map<ContextSection, Integer> currentTokens;
    private int totalTokens;

    public TrimContext(ContextSnapshot snapshot,
                       Map<ContextSection, Integer> budgets,
                       Map<ContextSection, ContextTrimStats> removedBySection,
                       ContextTokenEstimator estimator,
                       Map<ContextSection, Integer> currentTokens,
                       int totalTokens) {
        this.snapshot = snapshot;
        this.budgets = budgets;
        this.removedBySection = removedBySection;
        this.estimator = estimator;
        this.currentTokens = currentTokens;
        this.totalTokens = totalTokens;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public Map<ContextSection, Integer> getBudgets() {
        return budgets;
    }

    public Map<ContextSection, ContextTrimStats> getRemovedBySection() {
        return removedBySection;
    }

    public ContextTokenEstimator getEstimator() {
        return estimator;
    }

    public Map<ContextSection, Integer> getCurrentTokens() {
        return currentTokens;
    }

    public void setCurrentTokens(Map<ContextSection, Integer> currentTokens) {
        this.currentTokens = currentTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    /**
     * 刷新当前分段令牌与总令牌。
     */
    public void refreshTokens() {
        currentTokens = estimator.estimateSectionTokens(snapshot);
        totalTokens = estimator.sumTokens(currentTokens);
    }
}
