package com.example.agent.capabilities.memory.recall;

import java.util.Map;

/**
 * 召回上下文快照，承载从请求与上下文中解析出的召回参数。
 */
public class RecallContext {

    /**
     * 生效上下文字典。
     */
    private final Map<String, Object> effectiveContext;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 召回总开关。
     */
    private final boolean enabled;

    /**
     * 是否强制召回。
     */
    private final boolean force;

    /**
     * 触发召回的最小查询长度。
     */
    private final int minQueryLength;

    /**
     * 召回记录数上限。
     */
    private final int limit;

    /**
     * 摘要最大字符数。
     */
    private final int maxSummaryChars;

    /**
     * 单条记录最大字符数。
     */
    private final int maxRecordChars;

    /**
     * 是否包含压缩层记录。
     */
    private final boolean includeCompressed;

    public RecallContext(Map<String, Object> effectiveContext,
                         String workflowId,
                         boolean enabled,
                         boolean force,
                         int minQueryLength,
                         int limit,
                         int maxSummaryChars,
                         int maxRecordChars,
                         boolean includeCompressed) {
        this.effectiveContext = effectiveContext;
        this.workflowId = workflowId;
        this.enabled = enabled;
        this.force = force;
        this.minQueryLength = minQueryLength;
        this.limit = limit;
        this.maxSummaryChars = maxSummaryChars;
        this.maxRecordChars = maxRecordChars;
        this.includeCompressed = includeCompressed;
    }

    public Map<String, Object> getEffectiveContext() {
        return effectiveContext;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isForce() {
        return force;
    }

    public int getMinQueryLength() {
        return minQueryLength;
    }

    public int getLimit() {
        return limit;
    }

    public int getMaxSummaryChars() {
        return maxSummaryChars;
    }

    public int getMaxRecordChars() {
        return maxRecordChars;
    }

    public boolean isIncludeCompressed() {
        return includeCompressed;
    }
}

