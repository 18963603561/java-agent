package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.config.ContextCompressionProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 默认压缩触发策略，实现总预算与分段预算的统一判定。
 */
@Component
public class DefaultCompressionTriggerPolicy implements CompressionTriggerPolicy {

    private final ContextCompressionProperties properties;

    public DefaultCompressionTriggerPolicy(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolveTriggerReason(ContextBudgetAllocation allocation,
                                       Map<ContextSection, Integer> sectionTokens,
                                       Integer totalTokens) {
        if (!allocation.isAllocationEnabled()) {
            return null;
        }
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = properties == null || !properties.isTriggerOverTotalBudget()
                ? false
                : totalBudget != null && totalBudget > 0 && totalTokens != null && totalTokens > totalBudget;
        boolean overSection = properties == null || !properties.isTriggerOverSectionBudget()
                ? false
                : isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        if (!overTotal && !overSection) {
            return null;
        }
        if (overTotal && overSection) {
            return "OVER_TOTAL|OVER_SECTION";
        }
        return overTotal ? "OVER_TOTAL" : "OVER_SECTION";
    }

    @Override
    public boolean isOverBudget(ContextBudgetAllocation allocation,
                                Map<ContextSection, Integer> sectionTokens,
                                Integer totalTokens) {
        if (!allocation.isAllocationEnabled()) {
            return false;
        }
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = totalBudget != null && totalBudget > 0
                && totalTokens != null && totalTokens > totalBudget;
        boolean overSection = isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        return overTotal || overSection;
    }

    /**
     * 判断分段预算是否超限。
     */
    private boolean isOverSectionBudget(Map<ContextSection, Integer> sectionTokens,
                                        Map<ContextSection, Integer> budgets) {
        if (sectionTokens == null || budgets == null || budgets.isEmpty()) {
            return false;
        }
        for (Map.Entry<ContextSection, Integer> entry : sectionTokens.entrySet()) {
            ContextSection section = entry.getKey();
            if (section == null) {
                continue;
            }
            Integer budget = budgets.get(section);
            Integer value = entry.getValue();
            if (budget != null && value != null && value > budget) {
                return true;
            }
        }
        return false;
    }
}
