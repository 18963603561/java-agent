package com.example.agent.budget.trim;

import com.example.agent.capabilities.context.ContextSnapshot;
import java.util.List;

/**
 * 上下文裁剪结果。
 */
public class ContextPruneResult {

    /**
     * 裁剪后的快照。
     */
    private ContextSnapshot prunedSnapshot;

    /**
     * 裁剪项列表。
     */
    private List<PrunedItem> removedItems;

    /**
     * 裁剪摘要。
     */
    private String summary;

    public ContextSnapshot getPrunedSnapshot() {
        return prunedSnapshot;
    }

    public void setPrunedSnapshot(ContextSnapshot prunedSnapshot) {
        this.prunedSnapshot = prunedSnapshot;
    }

    public List<PrunedItem> getRemovedItems() {
        return removedItems;
    }

    public void setRemovedItems(List<PrunedItem> removedItems) {
        this.removedItems = removedItems;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}