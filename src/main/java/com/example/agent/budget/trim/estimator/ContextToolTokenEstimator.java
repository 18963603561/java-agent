package com.example.agent.budget.trim.estimator;

import com.example.agent.capabilities.context.model.ToolState;
import com.example.agent.capabilities.tools.ToolSummary;
import java.util.List;

/**
 * 工具相关令牌估算器。
 */
class ContextToolTokenEstimator {

    private final ContextTextTokenEstimator textEstimator;

    ContextToolTokenEstimator(ContextTextTokenEstimator textEstimator) {
        this.textEstimator = textEstimator;
    }

    /**
     * 估算工具状态令牌。
     */
    int estimateToolSummariesTokens(ToolState toolState) {
        if (toolState == null) {
            return 0;
        }
        return estimateToolSummariesTokens(toolState.getAvailableTools());
    }

    /**
     * 估算工具摘要列表令牌。
     */
    int estimateToolSummariesTokens(List<ToolSummary> tools) {
        if (tools == null) {
            return 0;
        }
        int total = 0;
        for (ToolSummary tool : tools) {
            total += estimateToolSummaryTokens(tool);
        }
        return total;
    }

    /**
     * 估算工具摘要令牌。
     */
    int estimateToolSummaryTokens(ToolSummary tool) {
        if (tool == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(tool.getToolName());
        total += textEstimator.estimateTokens(tool.getDescription());
        total += textEstimator.estimateTokens(tool.getTags());
        total += textEstimator.estimateTokens(tool.getCostLevel());
        total += textEstimator.estimateTokens(tool.getLatencyLevel());
        total += textEstimator.estimateTokens(tool.getAuthScope());
        return total;
    }

    /**
     * 估算工具摘要字符数。
     */
    int estimateToolSummaryChars(ToolSummary tool) {
        if (tool == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.safeLength(tool.getToolName());
        total += textEstimator.safeLength(tool.getDescription());
        if (tool.getTags() != null) {
            for (String tag : tool.getTags()) {
                total += textEstimator.safeLength(tag);
            }
        }
        total += textEstimator.safeLength(tool.getCostLevel());
        total += textEstimator.safeLength(tool.getLatencyLevel());
        total += textEstimator.safeLength(tool.getAuthScope());
        return total;
    }
}
