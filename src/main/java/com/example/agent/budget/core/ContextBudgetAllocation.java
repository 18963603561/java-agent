package com.example.agent.budget.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * 上下文预算分配结果。
 */
public class ContextBudgetAllocation {

    /**
     * 空分配对象，表示预算不可用或禁用的显式语义。
     */
    public static final ContextBudgetAllocation EMPTY = disabled(ContextBudgetAllocationState.DISABLED_BY_DEPENDENCY,
            "empty_allocation");

    /**
     * 预算版本号，默认 v1。
     */
    private String version = "v1";

    /**
     * 总令牌预算。
     */
    private Integer totalTokens;

    /**
     * 预留令牌预算。
     */
    private Integer reservedTokens;

    /**
     * 分段预算。
     */
    private Map<ContextSection, Integer> sectionTokens = Map.of();

    /**
     * 分配状态。
     */
    private ContextBudgetAllocationState allocationState = ContextBudgetAllocationState.ENABLED;

    /**
     * 分配状态原因编码。
     */
    private String allocationReason;

    /**
     * 判断预算分配是否启用。
     */
    public boolean isAllocationEnabled() {
        return allocationState == ContextBudgetAllocationState.ENABLED;
    }

    /**
     * 构建禁用态预算分配对象。
     *
     * @param state 禁用状态
     * @param reason 禁用原因
     * @return 禁用态预算对象
     */
    public static ContextBudgetAllocation disabled(ContextBudgetAllocationState state, String reason) {
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setAllocationState(state);
        allocation.setAllocationReason(reason);
        allocation.setTotalTokens(0);
        allocation.setReservedTokens(0);
        allocation.setSectionTokens(Map.of());
        return allocation;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

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

    public Integer getSystemAndDeveloperBudget() {
        return sectionToken(ContextSection.SYSTEM_POLICY) + sectionToken(ContextSection.DEVELOPER_POLICY);
    }

    public Integer getTaskIntentBudget() {
        return sectionToken(ContextSection.USER_INPUT);
    }

    public Integer getWorkingMemoryBudget() {
        return sectionToken(ContextSection.WORKING_MEMORY);
    }

    public Integer getRecalledMemoriesBudget() {
        return sectionToken(ContextSection.DOMAIN_KNOWLEDGE) + sectionToken(ContextSection.LONG_TERM_MEMORY);
    }

    public Integer getEvidencePackBudget() {
        return sectionToken(ContextSection.EVIDENCE_PACK);
    }

    public Integer getToolSummariesBudget() {
        return sectionToken(ContextSection.TOOL_SUMMARY);
    }

    public Integer getToolSchemaBudget() {
        return sectionToken(ContextSection.TOOL_SCHEMA);
    }

    public Integer getSlackBudget() {
        return sectionToken(ContextSection.SLACK);
    }

    public Map<ContextSection, Integer> getSectionTokens() {
        return sectionTokens;
    }

    public void setSectionTokens(Map<ContextSection, Integer> sectionTokens) {
        if (sectionTokens == null || sectionTokens.isEmpty()) {
            this.sectionTokens = Map.of();
            return;
        }
        EnumMap<ContextSection, Integer> copied = new EnumMap<>(ContextSection.class);
        for (Map.Entry<ContextSection, Integer> entry : sectionTokens.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            int value = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            copied.put(entry.getKey(), value);
        }
        this.sectionTokens = copied.isEmpty() ? Map.of() : Map.copyOf(copied);
    }

    public ContextBudgetAllocationState getAllocationState() {
        return allocationState;
    }

    public void setAllocationState(ContextBudgetAllocationState allocationState) {
        this.allocationState = allocationState == null
                ? ContextBudgetAllocationState.ENABLED
                : allocationState;
    }

    public String getAllocationReason() {
        return allocationReason;
    }

    public void setAllocationReason(String allocationReason) {
        this.allocationReason = allocationReason;
    }

    /**
     * 根据分段类型获取预算令牌数。
     */
    private int sectionToken(ContextSection section) {
        if (section == null || sectionTokens == null || sectionTokens.isEmpty()) {
            return 0;
        }
        Integer value = sectionTokens.get(section);
        return value == null ? 0 : value;
    }
}

