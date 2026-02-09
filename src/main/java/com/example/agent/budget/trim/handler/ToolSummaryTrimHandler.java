package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.capabilities.context.model.ToolState;
import com.example.agent.capabilities.tools.ToolSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具摘要分组裁剪处理器。
 */
public class ToolSummaryTrimHandler extends AbstractContextTrimHandler {

    @Override
    public ContextTrimSection section() {
        return ContextTrimSection.TOOL_SUMMARY;
    }

    @Override
    public void trimBySectionBudget(TrimContext context) {
        Integer budget = resolveBudget(context.getBudgets(), ContextSection.TOOL_SUMMARY);
        int current = context.getCurrentTokens().getOrDefault(ContextSection.TOOL_SUMMARY, 0);
        if (budget != null && current > budget) {
            trimToolSummaries(context, budget);
            context.refreshTokens();
        }
    }

    @Override
    public int trimByTotalBudget(TrimContext context, int excess) {
        if (excess <= 0) {
            return 0;
        }
        int current = context.getCurrentTokens().getOrDefault(ContextSection.TOOL_SUMMARY, 0);
        if (current <= 0) {
            return excess;
        }
        int target = Math.max(0, current - excess);
        int after = trimToolSummaries(context, target);
        context.refreshTokens();
        return Math.max(0, excess - Math.max(0, current - after));
    }

    private int trimToolSummaries(TrimContext context,
                                  int targetTokens) {
        ToolState toolState = context.getSnapshot().getToolState();
        if (toolState == null || toolState.getAvailableTools() == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        List<ToolSummary> tools = mutableCopy(toolState.getAvailableTools());
        int currentTokens = estimator.estimateToolSummariesTokens(tools);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        for (ToolSummary tool : tools) {
            if (tool == null || tool.getDescription() == null) {
                continue;
            }
            String desc = tool.getDescription();
            String trimmed = trimText(desc, MAX_TOOL_DESC_CHARS);
            if (trimmed != null && trimmed.length() < desc.length()) {
                recordRemoved(context, ContextSection.TOOL_SUMMARY, 1,
                        desc.length() - trimmed.length(),
                        estimator.estimateTokensByChars(desc.length() - trimmed.length()));
                tool.setDescription(trimmed);
            }
        }
        currentTokens = estimator.estimateToolSummariesTokens(tools);
        if (currentTokens > targetTokens) {
            Set<String> selected = new HashSet<>();
            if (toolState.getSelectedTools() != null) {
                selected.addAll(toolState.getSelectedTools());
            }
            List<ToolSummary> remaining = new ArrayList<>(tools);
            for (int i = remaining.size() - 1; i >= 0 && currentTokens > targetTokens; i--) {
                ToolSummary tool = remaining.get(i);
                String name = tool != null ? tool.getToolName() : null;
                if (name != null && selected.contains(name)) {
                    continue;
                }
                remaining.remove(i);
                int removedChars = estimator.estimateToolSummaryChars(tool);
                int removedTokens = estimator.estimateToolSummaryTokens(tool);
                recordRemoved(context, ContextSection.TOOL_SUMMARY, 1, removedChars, removedTokens);
                currentTokens = estimator.estimateToolSummariesTokens(remaining);
            }
            for (int i = remaining.size() - 1; i >= 0 && currentTokens > targetTokens; i--) {
                ToolSummary tool = remaining.get(i);
                remaining.remove(i);
                int removedChars = estimator.estimateToolSummaryChars(tool);
                int removedTokens = estimator.estimateToolSummaryTokens(tool);
                recordRemoved(context, ContextSection.TOOL_SUMMARY, 1, removedChars, removedTokens);
                currentTokens = estimator.estimateToolSummariesTokens(remaining);
            }
            tools = remaining;
        }
        toolState.setAvailableTools(tools.isEmpty() ? null : tools);
        return estimator.estimateToolSummariesTokens(tools);
    }
}
