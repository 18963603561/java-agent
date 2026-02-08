package com.example.agent.capabilities.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆召回配置，用于控制自动记忆检索开关与裁剪策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.memory.recall")
public class MemoryRecallProperties {

    /**
     * 是否开启自动记忆召回。
     */
    private boolean enabled = true;

    /**
     * 触发召回的最小查询长度。
     */
    private int minQueryLength = 4;

    /**
     * 召回条数上限。
     */
    private int limit = 10;

    /**
     * 是否包含压缩层记忆。
     */
    private boolean includeCompressed = true;

    /**
     * 召回摘要的最大字符数。
     */
    private int maxSummaryChars = 800;

    /**
     * 单条记忆内容/摘要的最大字符数。
     */
    private int maxRecordChars = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinQueryLength() {
        return minQueryLength;
    }

    public void setMinQueryLength(int minQueryLength) {
        this.minQueryLength = minQueryLength;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isIncludeCompressed() {
        return includeCompressed;
    }

    public void setIncludeCompressed(boolean includeCompressed) {
        this.includeCompressed = includeCompressed;
    }

    public int getMaxSummaryChars() {
        return maxSummaryChars;
    }

    public void setMaxSummaryChars(int maxSummaryChars) {
        this.maxSummaryChars = maxSummaryChars;
    }

    public int getMaxRecordChars() {
        return maxRecordChars;
    }

    public void setMaxRecordChars(int maxRecordChars) {
        this.maxRecordChars = maxRecordChars;
    }
}
