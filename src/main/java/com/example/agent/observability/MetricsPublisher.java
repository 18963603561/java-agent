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
     * 记录耗时指标。
     *
     * @param name 指标名称
     * @param millis 耗时毫秒
     */
    public void recordTime(String name, long millis) {
        meterRegistry.timer(name).record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
