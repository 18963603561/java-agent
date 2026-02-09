package com.example.agent.capabilities.context;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.budget.trim.ContextTrimReport;
import com.example.agent.capabilities.context.model.BuildMetrics;
import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 上下文构建成功结果。
 *
 * <p>该对象只表达成功语义，构建失败通过异常抛出，不再通过空结果表达。
 */
public class ContextBuildResult {

    /**
     * 上下文快照，构建成功时必填。
     */
    private final ContextSnapshot snapshot;

    /**
     * 预算分配结果，可为空。
     */
    private final ContextBudgetAllocation budgetAllocation;

    /**
     * 剪枝结果，可为空。
     */
    private final ContextPruneResult pruneResult;

    /**
     * 裁剪报告，可为空。
     */
    private final ContextTrimReport trimReport;

    /**
     * 构建指标，构建成功时必填。
     */
    private final BuildMetrics metrics;

    /**
     * 构造成功结果。
     *
     * @param snapshot 上下文快照，不允许为空
     * @param budgetAllocation 预算分配
     * @param pruneResult 剪枝结果
     * @param trimReport 裁剪报告
     * @param metrics 构建指标，不允许为空
     */
    public ContextBuildResult(ContextSnapshot snapshot,
                              ContextBudgetAllocation budgetAllocation,
                              ContextPruneResult pruneResult,
                              ContextTrimReport trimReport,
                              BuildMetrics metrics) {
        if (snapshot == null) {
            throw new IllegalArgumentException("context snapshot must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("build metrics must not be null");
        }
        this.snapshot = snapshot;
        this.budgetAllocation = budgetAllocation;
        this.pruneResult = pruneResult;
        this.trimReport = trimReport;
        this.metrics = metrics;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public ContextBudgetAllocation getBudgetAllocation() {
        return budgetAllocation;
    }

    public ContextPruneResult getPruneResult() {
        return pruneResult;
    }

    public ContextTrimReport getTrimReport() {
        return trimReport;
    }

    public BuildMetrics getMetrics() {
        return metrics;
    }
}
