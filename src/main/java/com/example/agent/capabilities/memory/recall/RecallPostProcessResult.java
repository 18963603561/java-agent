package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.memory.model.MemoryRecord;
import java.util.List;

/**
 * 召回后处理结果，封装可用于注入上下文的输出内容。
 */
public class RecallPostProcessResult {

    /**
     * 处理后的记录列表。
     */
    private final List<MemoryRecord> records;

    /**
     * 汇总摘要。
     */
    private final String summary;

    /**
     * 脱敏替换次数。
     */
    private final int redactionsAppliedCount;

    public RecallPostProcessResult(List<MemoryRecord> records, String summary, int redactionsAppliedCount) {
        this.records = records;
        this.summary = summary;
        this.redactionsAppliedCount = redactionsAppliedCount;
    }

    public List<MemoryRecord> getRecords() {
        return records;
    }

    public String getSummary() {
        return summary;
    }

    public int getRedactionsAppliedCount() {
        return redactionsAppliedCount;
    }
}


