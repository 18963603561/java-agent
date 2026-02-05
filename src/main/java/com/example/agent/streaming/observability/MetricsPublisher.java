package com.example.agent.streaming.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 指标发布器，用于统一记录关键业务指标。
 */
@Component
public class MetricsPublisher {

    private final MeterRegistry meterRegistry;

    public MetricsPublisher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 递增计数器指标。
     *
     * @param name 指标名称
     */
    public void increment(String name) {
        meterRegistry.counter(name).increment();
    }

    /**
     * 递增计数器指标（带链路标识）。
     *
     * @param name 指标名称
     * @param traceId 链路标识
     */
    public void increment(String name, String traceId) {
        meterRegistry.counter(name, "traceId", safeTag(traceId)).increment();
    }

    /**
     * 递增计数器指标（自定义标签）。
     *
     * @param name 指标名称
     * @param tags 标签键值对
     */
    public void incrementWithTags(String name, String... tags) {
        meterRegistry.counter(name, tags).increment();
    }

    /**
     * 递增计数器指标（自定义标签与增量）。
     *
     * @param name 指标名称
     * @param amount 增量
     * @param tags 标签键值对
     */
    public void incrementWithTags(String name, double amount, String... tags) {
        meterRegistry.counter(name, tags).increment(amount);
    }

    /**
     * 记录耗时指标。
     *
     * @param name 指标名称
     * @param millis 耗时毫秒
     */
    public void recordTime(String name, long millis) {
        meterRegistry.timer(name).record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录耗时指标（带链路标识）。
     *
     * @param name 指标名称
     * @param millis 耗时毫秒
     * @param traceId 链路标识
     */
    public void recordTime(String name, long millis, String traceId) {
        meterRegistry.timer(name, "traceId", safeTag(traceId))
                .record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录分布类指标。
     *
     * @param name 指标名称
     * @param value 指标值
     */
    public void recordSummary(String name, double value) {
        meterRegistry.summary(name).record(value);
    }

    /**
     * 记录分布类指标（自定义标签）。
     *
     * @param name 指标名称
     * @param value 指标值
     * @param tags 标签键值对
     */
    public void recordSummary(String name, double value, String... tags) {
        meterRegistry.summary(name, tags).record(value);
    }

    private String safeTag(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }
        return traceId;
    }
}
