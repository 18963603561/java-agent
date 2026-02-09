package com.example.agent.budget.trim.model;

import com.example.agent.capabilities.memory.model.MemoryRecord;

/**
 * 压缩执行结果，统一封装外部压缩调用产物与耗时信息。
 */
public class CompressionExecutionResult {

    /**
     * 压缩结果记忆记录。
     */
    private MemoryRecord compressed;

    /**
     * 压缩耗时毫秒。
     */
    private long durationMs;

    /**
     * 是否执行成功。
     */
    private boolean success;

    public MemoryRecord getCompressed() {
        return compressed;
    }

    public void setCompressed(MemoryRecord compressed) {
        this.compressed = compressed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
