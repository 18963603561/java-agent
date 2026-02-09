package com.example.agent.planning.context;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidenceStats;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 规划提示词摘要工厂。
 *
 * <p>用途：负责从规划上下文提取提示词摘要，降低上下文对象职责密度。
 */
public final class PlanningPromptSummaryFactory {

    private PlanningPromptSummaryFactory() {
    }

    /**
     * 构建提示词摘要。
     *
     * @param context 规划上下文
     * @return 摘要映射
     */
    public static Map<String, Object> build(PlanningContext context) {
        Map<String, Object> summary = new HashMap<>();
        if (context == null) {
            summary.put(PlanningFieldKeys.SUMMARY, "(summary disabled)");
            return summary;
        }
        String snapshotId = context.getString(PlanningContextKeys.SNAPSHOT_ID);
        if (snapshotId == null) {
            ContextSnapshot snapshot = context.getContextSnapshot();
            if (snapshot != null && StringUtils.hasText(snapshot.getSnapshotId())) {
                snapshotId = snapshot.getSnapshotId();
            }
        }
        if (StringUtils.hasText(snapshotId)) {
            summary.put(PlanningContextKeys.SNAPSHOT_ID, snapshotId);
        }

        Integer tokenBudget = resolveTokenBudget(context);
        if (tokenBudget != null) {
            summary.put(PlanningFieldKeys.TOKEN_BUDGET, tokenBudget);
        }

        Integer memoryItems = resolveMemoryCount(context);
        if (memoryItems != null) {
            summary.put(PlanningFieldKeys.MEMORY_ITEMS, memoryItems);
        }

        List<String> tools = context.getTools();
        if (!tools.isEmpty()) {
            summary.put(PlanningContextKeys.TOOLS, tools);
        }

        Integer evidenceCount = resolveEvidenceCount(context);
        if (evidenceCount != null) {
            summary.put(PlanningFieldKeys.EVIDENCE_COUNT, evidenceCount);
        }

        if (summary.isEmpty()) {
            summary.put(PlanningFieldKeys.SUMMARY, "(summary disabled)");
        }
        return summary;
    }

    private static Integer resolveTokenBudget(PlanningContext context) {
        ContextBudgetAllocation allocation = context.getContextBudget();
        Integer tokenBudget = allocation != null && allocation.isAllocationEnabled()
                ? allocation.getTotalTokens()
                : null;
        if (tokenBudget == null) {
            tokenBudget = context.getInteger(PlanningContextKeys.BUDGET_THRESHOLD_TOKENS);
        }
        return tokenBudget;
    }

    private static Integer resolveMemoryCount(PlanningContext context) {
        Object memoryObj = context.get(PlanningContextKeys.MEMORY);
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Object count = memoryMap.get(PlanningContextKeys.COUNT);
            if (count instanceof Number number) {
                return number.intValue();
            }
            if (count != null) {
                try {
                    return Integer.parseInt(count.toString());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        ContextSnapshot snapshot = context.getContextSnapshot();
        if (snapshot != null && snapshot.getWorkingMemory() != null) {
            return snapshot.getWorkingMemory().getWorkingMemoryItems();
        }
        return null;
    }

    private static Integer resolveEvidenceCount(PlanningContext context) {
        EvidencePack evidencePack = resolveEvidencePack(context);
        if (evidencePack == null) {
            return null;
        }
        EvidenceStats stats = evidencePack.getStats();
        if (stats != null && stats.getResearchCount() != null) {
            return stats.getResearchCount();
        }
        if (stats != null && stats.getTotalCount() != null) {
            return stats.getTotalCount();
        }
        if (evidencePack.getEvidences() != null) {
            return evidencePack.getEvidences().size();
        }
        return null;
    }

    private static EvidencePack resolveEvidencePack(PlanningContext context) {
        Object value = context.get(PlanningContextKeys.EVIDENCE_PACK);
        if (value instanceof EvidencePack pack) {
            return pack;
        }
        return null;
    }
}

