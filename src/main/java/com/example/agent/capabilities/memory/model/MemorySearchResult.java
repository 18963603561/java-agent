package com.example.agent.capabilities.memory.model;

import java.util.List;

/**
 * 记忆搜索结果。
 */
public class MemorySearchResult {

    private List<MemoryRecord> records;

    public MemorySearchResult() {
    }

    public MemorySearchResult(List<MemoryRecord> records) {
        this.records = records;
    }

    public List<MemoryRecord> getRecords() {
        return records;
    }

    public void setRecords(List<MemoryRecord> records) {
        this.records = records;
    }
}
