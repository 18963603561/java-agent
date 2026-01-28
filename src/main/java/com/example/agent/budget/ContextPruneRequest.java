package com.example.agent.budget;

import com.example.agent.context.ContextPolicy;
import com.example.agent.context.ContextSnapshot;

/**
 * 上下文裁剪请求。
 */
public class ContextPruneRequest {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果。
     */
    private ContextBudgetAllocation allocation;

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
        this.allocation = allocation;
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
        this.allocation = allocation;
    }

    public ContextPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ContextPolicy policy) {
        this.policy = policy;
    }
}