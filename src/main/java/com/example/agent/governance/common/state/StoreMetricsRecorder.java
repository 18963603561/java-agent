package com.example.agent.governance.common.state;

/**
 * 状态仓储指标与日志回调。
 *
 * @param <T> 记录类型
 */
public interface StoreMetricsRecorder<T> {

    /**
     * 记录容量拒绝。
     *
     * @param value 被拒绝记录
     * @param currentSize 当前大小
     * @param maxSize 最大容量
     */
    void onCapacityRejected(T value, int currentSize, int maxSize);

    /**
     * 记录清理完成。
     *
     * @param removed 清理数量
     * @param currentSize 当前大小
     */
    void onCleanup(int removed, int currentSize);
}

