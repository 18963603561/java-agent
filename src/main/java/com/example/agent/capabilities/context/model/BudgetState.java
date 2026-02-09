package com.example.agent.capabilities.context.model;

import com.example.agent.budget.core.ContextBudgetAllocationState;

/**
 * 预算状态信息。
 */
public class BudgetState {

    /**
     * 已分配令牌数。
     */
    private Integer allocatedTokens;

    /**
     * 已使用令牌数。
     */
    private Integer usedTokens;

    /**
     * 剩余令牌数。
     */
    private Integer remainingTokens;

    /**
     * 预算分配状态。
     */
    private ContextBudgetAllocationState allocationState;

    /**
     * 预算分配原因编码。
     */
    private String allocationReason;

    public Integer getAllocatedTokens() {
        return allocatedTokens;
    }

    public void setAllocatedTokens(Integer allocatedTokens) {
        this.allocatedTokens = allocatedTokens;
    }

    public Integer getUsedTokens() {
        return usedTokens;
    }

    public void setUsedTokens(Integer usedTokens) {
        this.usedTokens = usedTokens;
    }

    public Integer getRemainingTokens() {
        return remainingTokens;
    }

    public void setRemainingTokens(Integer remainingTokens) {
        this.remainingTokens = remainingTokens;
    }

    public ContextBudgetAllocationState getAllocationState() {
        return allocationState;
    }

    public void setAllocationState(ContextBudgetAllocationState allocationState) {
        this.allocationState = allocationState;
    }

    public String getAllocationReason() {
        return allocationReason;
    }

    public void setAllocationReason(String allocationReason) {
        this.allocationReason = allocationReason;
    }
}
