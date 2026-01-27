package com.example.agent.memory;

import java.util.List;

/**
 * 记忆召回结果，封装命中状态、摘要与记录列表。
 */
public class MemoryRecallResult {

    /**
     * 是否命中并返回记忆。
     */
    private final boolean used;

    /**
     * 跳过或失败原因。
     */
    private final String reason;

    /**
     * 召回到的记忆列表（已裁剪）。
     */
    private final List<MemoryRecord> records;

    /**
     * 供上下文注入的摘要文本。
     */
    private final String summary;

    private MemoryRecallResult(boolean used, String reason, List<MemoryRecord> records, String summary) {
        this.used = used;
        this.reason = reason;
        this.records = records == null ? List.of() : records;
        this.summary = summary;
    }

    /**
     * 创建跳过结果。
     *
     * @param reason 跳过原因
     * @return 跳过结果
     */
    public static MemoryRecallResult skipped(String reason) {
        return new MemoryRecallResult(false, reason, List.of(), null);
    }

    /**
     * 创建命中结果。
     *
     * @param records 召回记录
     * @param summary 摘要文本
     * @return 命中结果
     */
    public static MemoryRecallResult hit(List<MemoryRecord> records, String summary) {
        return new MemoryRecallResult(true, "ok", records, summary);
    }

    public boolean isUsed() {
        return used;
    }

    public String getReason() {
        return reason;
    }

    public List<MemoryRecord> getRecords() {
        return records;
    }

    public String getSummary() {
        return summary;
    }

    public int getCount() {
        return records == null ? 0 : records.size();
    }
}
