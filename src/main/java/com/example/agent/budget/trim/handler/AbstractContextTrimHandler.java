package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.model.ContextTrimStats;
import com.example.agent.capabilities.context.model.Citation;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.ToolCallState;
import com.example.agent.capabilities.tools.ToolSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 裁剪处理器基类，提供公共工具方法。
 */
public abstract class AbstractContextTrimHandler implements ContextTrimHandler {

    protected static final int MAX_RESULT_DIGEST_CHARS = 200;
    protected static final int MAX_ARGS_DIGEST_CHARS = 200;
    protected static final int MAX_TOOL_DESC_CHARS = 160;

    @Override
    public abstract ContextTrimSection section();

    @Override
    public abstract void trimBySectionBudget(TrimContext context);

    @Override
    public abstract int trimByTotalBudget(TrimContext context, int excess);

    /**
     * 解析分段预算。
     */
    protected Integer resolveBudget(Map<ContextSection, Integer> budgets, ContextSection section) {
        if (budgets == null || section == null) {
            return null;
        }
        Integer value = budgets.get(section);
        if (value == null) {
            return null;
        }
        return Math.max(0, value);
    }

    /**
     * 记录移除统计。
     */
    protected void recordRemoved(TrimContext context,
                                 ContextSection section,
                                 int count,
                                 int chars,
                                 int tokens) {
        if (count <= 0 && chars <= 0 && tokens <= 0) {
            return;
        }
        ContextTrimStats stats = context.getRemovedBySection().computeIfAbsent(section, key -> new ContextTrimStats());
        stats.addRemoved(count, chars, tokens);
    }

    /**
     * 安全裁剪文本。
     */
    protected String trimText(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars <= 0) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /**
     * 按目标令牌裁剪文本。
     */
    protected String trimTextByTokens(String text, int targetTokens) {
        if (text == null) {
            return null;
        }
        if (targetTokens <= 0) {
            return "";
        }
        int maxChars = targetTokens * 4;
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /**
     * 复制列表为可变副本。
     */
    protected <T> List<T> mutableCopy(List<T> list) {
        if (list == null) {
            return null;
        }
        return new ArrayList<>(list);
    }

    /**
     * 估算工具摘要字符数。
     */
    protected int estimateToolSummaryChars(ContextTokenEstimator estimator, ToolSummary tool) {
        return estimator.estimateToolSummaryChars(tool);
    }

    /**
     * 估算引用字符数。
     */
    protected int estimateCitationChars(ContextTokenEstimator estimator, Citation citation) {
        return estimator.estimateCitationChars(citation);
    }

    /**
     * 估算记忆引用字符数。
     */
    protected int estimateMemoryRefChars(ContextTokenEstimator estimator, MemoryRef ref) {
        return estimator.estimateMemoryRefChars(ref);
    }

    /**
     * 估算工具调用字符数。
     */
    protected int estimateToolCallStateChars(ContextTokenEstimator estimator, ToolCallState call) {
        return estimator.estimateToolCallStateChars(call);
    }
}
