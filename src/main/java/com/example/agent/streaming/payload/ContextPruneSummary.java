package com.example.agent.streaming.payload;

import java.util.Map;

/**
 * 上下文裁剪摘要，用于事件载荷中的裁剪统计。
 */
public class ContextPruneSummary {

    /**
     * 被移除条目数量。
     */
    private Integer removedCount;

    /**
     * 被移除条目类型统计，key 为类型，value 为数量。
     */
    private Map<String, Integer> removedItemTypes;

    /**
     * 裁剪摘要说明。
     */
    private String summary;

    public Integer getRemovedCount() {
        return removedCount;
    }

    public void setRemovedCount(Integer removedCount) {
        this.removedCount = removedCount;
    }

    public Map<String, Integer> getRemovedItemTypes() {
        return removedItemTypes;
    }

    public void setRemovedItemTypes(Map<String, Integer> removedItemTypes) {
        this.removedItemTypes = removedItemTypes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
