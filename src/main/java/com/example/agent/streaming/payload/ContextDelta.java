package com.example.agent.streaming.payload;

import java.util.List;
import java.util.Map;

/**
 * 上下文变更摘要，用于描述本次事件的变化范围与裁剪结果。
 */
public class ContextDelta {

    /**
     * 发生变化的上下文分段名称列表。
     */
    private List<String> changedSections;

    /**
     * 变更摘要说明。
     */
    private String summary;

    /**
     * 被移除的条目数量。
     */
    private Integer removedCount;

    /**
     * 被移除条目类型统计，key 为类型，value 为数量。
     */
    private Map<String, Integer> removedItemTypes;

    public List<String> getChangedSections() {
        return changedSections;
    }

    public void setChangedSections(List<String> changedSections) {
        this.changedSections = changedSections;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

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
}
