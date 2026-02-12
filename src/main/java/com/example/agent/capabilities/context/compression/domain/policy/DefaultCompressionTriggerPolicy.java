package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 默认压缩触发策略，实现总预算、分段预算与比例阈值统一判定。
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
        // 守卫：预算未启用时不触发压缩，避免无意义计算与日志噪声。
        if (!allocation.isAllocationEnabled()) {
            return null;
        }
        Integer totalBudget = allocation.getTotalTokens();
        // 总预算判定：仅在配置开启且预算存在时生效。
        boolean overTotal = isTriggerOverTotalBudgetEnabled()
                && totalBudget != null
                && totalBudget > 0
                && totalTokens != null
                && totalTokens > totalBudget;
        // 分段预算判定：命中任一分区超限即视为触发。
        boolean overSection = isTriggerOverSectionBudgetEnabled()
                && isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        // 比例阈值判定：总 token 占预算比超过 triggerRatio 时触发。
        boolean overRatio = isOverTriggerRatio(totalBudget, totalTokens);
        if (!overTotal && !overSection && !overRatio) {
            return null;
        }
        StringBuilder reason = new StringBuilder();
        // 原因拼接：保留多原因标记，便于观测聚合。
        appendReason(reason, overTotal, "OVER_TOTAL");
        // 原因拼接：标记分区超限场景。
        appendReason(reason, overSection, "OVER_SECTION");
        // 原因拼接：标记比例触发场景。
        appendReason(reason, overRatio, "OVER_RATIO");
        // 返回触发原因：供上游记录指标与审计事件。
        return reason.toString();
    }

    @Override
    public boolean isOverBudget(ContextBudgetAllocation allocation,
                                Map<ContextSection, Integer> sectionTokens,
                                Integer totalTokens) {
        // 守卫：预算未启用时不做超预算判定。
        if (!allocation.isAllocationEnabled()) {
            return false;
        }
        Integer totalBudget = allocation.getTotalTokens();
        // 总预算判断：压缩后仍超预算时用于告警。
        boolean overTotal = totalBudget != null
                && totalBudget > 0
                && totalTokens != null
                && totalTokens > totalBudget;
        // 分段预算判断：任一分区超限则返回 true。
        boolean overSection = isOverSectionBudget(sectionTokens, allocation.getSectionTokens());
        // 比例预算判断：压缩后仍高于目标比例则返回 true。
        boolean overTargetRatio = isOverTargetRatio(totalBudget, totalTokens);
        // 汇总返回：任一维度超限即认为仍超预算。
        return overTotal || overSection || overTargetRatio;
    }

    /**
     * 判断分段预算是否超限。
     */
    private boolean isOverSectionBudget(Map<ContextSection, Integer> sectionTokens,
                                        Map<ContextSection, Integer> budgets) {
        if (sectionTokens == null || budgets == null || budgets.isEmpty()) {
            return false;
        }
        // 遍历分区 token：逐项比对预算，命中即提前返回。
        for (Map.Entry<ContextSection, Integer> entry : sectionTokens.entrySet()) {
            ContextSection section = entry.getKey();
            // 安全判断：空分区键直接跳过，避免污染判定结果。
            if (section == null) {
                continue;
            }
            Integer budget = budgets.get(section);
            Integer value = entry.getValue();
            // 预算判断：当分区 token 大于预算时视为超限。
            if (budget != null && value != null && value > budget) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否超过触发比例阈值。
     */
    private boolean isOverTriggerRatio(Integer totalBudget, Integer totalTokens) {
        if (totalBudget == null || totalBudget <= 0 || totalTokens == null || totalTokens <= 0) {
            return false;
        }
        Double triggerRatio = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getCompressionTriggerRatio()
                : null;
        if (triggerRatio == null || triggerRatio <= 0D) {
            return false;
        }
        double usageRatio = (double) totalTokens / totalBudget;
        return usageRatio >= triggerRatio;
    }

    /**
     * 判断是否超过目标比例阈值。
     */
    private boolean isOverTargetRatio(Integer totalBudget, Integer totalTokens) {
        if (totalBudget == null || totalBudget <= 0 || totalTokens == null || totalTokens <= 0) {
            return false;
        }
        Double targetRatio = properties != null && properties.getTrigger() != null
                ? properties.getTrigger().getCompressionTargetRatio()
                : null;
        if (targetRatio == null || targetRatio <= 0D) {
            return false;
        }
        double usageRatio = (double) totalTokens / totalBudget;
        return usageRatio > targetRatio;
    }

    /**
     * 判断是否开启总预算触发。
     */
    private boolean isTriggerOverTotalBudgetEnabled() {
        return properties != null
                && properties.getTrigger() != null
                && properties.getTrigger().isOverTotalBudgetEnabled();
    }

    /**
     * 判断是否开启分段预算触发。
     */
    private boolean isTriggerOverSectionBudgetEnabled() {
        return properties != null
                && properties.getTrigger() != null
                && properties.getTrigger().isOverSectionBudgetEnabled();
    }

    /**
     * 追加触发原因片段。
     */
    private void appendReason(StringBuilder builder, boolean matched, String reasonCode) {
        if (!matched) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('|');
        }
        builder.append(reasonCode);
    }
}


