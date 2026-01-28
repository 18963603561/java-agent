package com.example.agent.context;

import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextPruneResult;

/**
 * 上下文构建结果。
 */
public class ContextBuildResult {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果。
     */
    private ContextBudgetAllocation budgetAllocation;

    /**
     * 裁剪结果。
     */
    private ContextPruneResult pruneResult;

    /**
     * 构建指标。
     */
    private BuildMetrics metrics;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public ContextBudgetAllocation getBudgetAllocation() {
        return budgetAllocation;
    }

    public void setBudgetAllocation(ContextBudgetAllocation budgetAllocation) {
        this.budgetAllocation = budgetAllocation;
    }

    public ContextPruneResult getPruneResult() {
        return pruneResult;
    }

    public void setPruneResult(ContextPruneResult pruneResult) {
        this.pruneResult = pruneResult;
    }

    public BuildMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(BuildMetrics metrics) {
        this.metrics = metrics;
    }
}