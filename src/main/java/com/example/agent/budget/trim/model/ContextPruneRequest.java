package com.example.agent.budget.trim.model;

import com.example.agent.capabilities.context.model.ContextPolicy;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.budget.core.ContextBudgetAllocation;

/**
 * 上下文裁剪请求。
 */
public class ContextPruneRequest {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果，默认使用禁用态空分配对象。
     */
    private ContextBudgetAllocation allocation = ContextBudgetAllocation.EMPTY;

    /**
     * 裁剪策略。
     */
    private ContextPolicy policy;

    public ContextPruneRequest() {
    }

    public ContextPruneRequest(ContextSnapshot snapshot,
                               ContextBudgetAllocation allocation,
                               ContextPolicy policy) {
        this.snapshot = snapshot;
        setAllocation(allocation);
        this.policy = policy;
    }

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public ContextBudgetAllocation getAllocation() {
        return allocation;
    }

    public void setAllocation(ContextBudgetAllocation allocation) {
        this.allocation = allocation == null ? ContextBudgetAllocation.EMPTY : allocation;
    }

    public ContextPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ContextPolicy policy) {
        this.policy = policy;
    }
}
