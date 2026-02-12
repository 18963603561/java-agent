package com.example.agent.capabilities.context.compression.application.port;

import com.example.agent.capabilities.context.compression.observability.CompressionObservation;

/**
 * 压缩可观测性端口。
 *
 * <p>用途：为压缩编排提供统一指标与事件记录接口，隔离具体观测实现。</p>
 */
public interface CompressionTelemetryPort {

    /**
     * 增加计数指标。
     *
     * @param metric 指标名
     */
    void increment(String metric);

    /**
     * 按标签增加计数指标。
     *
     * @param metric 指标名
     * @param tags 标签键值对
     */
    void incrementWithTags(String metric, String... tags);

    /**
     * 记录汇总类指标。
     *
     * @param metric 指标名
     * @param value 指标值
     */
    void recordSummary(String metric, double value);

    /**
     * 记录压缩观测。
     *
     * @param observation 压缩观测对象
     */
    void recordObservation(CompressionObservation observation);
}
