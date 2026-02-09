package com.example.agent.streaming.payload;

import com.example.agent.budget.core.ContextBudgetAllocationState;
import java.util.Map;

/**
 * 上下文预算摘要，用于事件载荷中的预算统计。
 */
public class ContextBudgetSummary {

    /**
     * 预算分配状态。
     */
    private ContextBudgetAllocationState allocationState;

    /**
     * 预算分配原因编码。
     */
    private String allocationReason;

    /**
     * 总预算令牌数。
     */
    private Integer totalTokens;

    /**
     * 预留令牌数。
     */
    private Integer reservedTokens;

    /**
     * 分段预算明细，key 为分段名称，value 为令牌数。
     */
    private Map<String, Integer> sectionTokens;

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getReservedTokens() {
        return reservedTokens;
    }

    public void setReservedTokens(Integer reservedTokens) {
        this.reservedTokens = reservedTokens;
    }

    public Map<String, Integer> getSectionTokens() {
        return sectionTokens;
    }

    public void setSectionTokens(Map<String, Integer> sectionTokens) {
        this.sectionTokens = sectionTokens;
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
