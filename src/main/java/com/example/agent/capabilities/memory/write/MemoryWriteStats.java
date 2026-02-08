package com.example.agent.capabilities.memory.write;

/**
 * 记忆写入统计对象，用于聚合写入阶段的结果计数。
 */
public class MemoryWriteStats {

    /**
     * 成功写入记录数。
     */
    private int saved;

    /**
     * 拒写次数。
     */
    private int writesRejectedCount;

    /**
     * 脱敏命中次数。
     */
    private int redactionsAppliedCount;

    public int getSaved() {
        return saved;
    }

    public int getWritesRejectedCount() {
        return writesRejectedCount;
    }

    public int getRedactionsAppliedCount() {
        return redactionsAppliedCount;
    }

    /**
     * 记录一次成功写入。
     */
    public void incrementSaved() {
        this.saved++;
    }

    /**
     * 记录一次拒写。
     */
    public void incrementRejected() {
        this.writesRejectedCount++;
    }

    /**
     * 增加脱敏命中计数。
     *
     * @param delta 新增计数
     */
    public void addRedactions(int delta) {
        if (delta > 0) {
            this.redactionsAppliedCount += delta;
        }
    }
}

