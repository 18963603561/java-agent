package com.example.agent.observability;

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
     * 递增计数器指标（带 traceId 标签）。
     *
     * @param name 指标名称
     * @param traceId 链路标识
     */
    public void increment(String name, String traceId) {
        meterRegistry.counter(name, "traceId", safeTag(traceId)).increment();
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
     * 记录耗时指标（带 traceId 标签）。
     *
     * @param name 指标名称
     * @param millis 耗时毫秒
     * @param traceId 链路标识
     */
    public void recordTime(String name, long millis, String traceId) {
        meterRegistry.timer(name, "traceId", safeTag(traceId))
                .record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private String safeTag(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }
        return traceId;
    }
}
