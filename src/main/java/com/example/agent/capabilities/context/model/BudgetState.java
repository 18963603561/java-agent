package com.example.agent.capabilities.context.model;

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
}