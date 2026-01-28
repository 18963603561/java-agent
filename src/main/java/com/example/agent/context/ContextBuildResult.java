package com.example.agent.context;

import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextPruneResult;
import com.example.agent.budget.ContextTrimReport;

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
     * 裁剪报告。
     */
    private ContextTrimReport trimReport;

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

    public ContextTrimReport getTrimReport() {
        return trimReport;
    }

    public void setTrimReport(ContextTrimReport trimReport) {
        this.trimReport = trimReport;
    }

    public BuildMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(BuildMetrics metrics) {
        this.metrics = metrics;
    }
}
