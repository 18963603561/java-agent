package com.example.agent.budget.trim;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.token.ContextBudgetPolicy;

/**
 * 上下文裁剪请求。
 */
public class ContextTrimRequest {

    /**
     * 上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 预算分配结果。
     */
    private ContextBudgetAllocation allocation;

    /**
     * 预算策略。
     */
    private ContextBudgetPolicy policy;

    public ContextTrimRequest() {
    }

    public ContextTrimRequest(ContextSnapshot snapshot,
                              ContextBudgetAllocation allocation,
                              ContextBudgetPolicy policy) {
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

    public ContextBudgetPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ContextBudgetPolicy policy) {
        this.policy = policy;
    }
}
