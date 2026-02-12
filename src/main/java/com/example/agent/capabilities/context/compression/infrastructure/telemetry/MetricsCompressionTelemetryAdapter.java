package com.example.agent.capabilities.context.compression.infrastructure.telemetry;

import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.springframework.stereotype.Component;

/**
 * 基于指标发布器的压缩观测适配器。
 */
@Component
public class MetricsCompressionTelemetryAdapter implements CompressionTelemetryPort {

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    public MetricsCompressionTelemetryAdapter(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public void increment(String metric) {
        // 调用指标发布器：记录压缩计数类观测信息。
        if (metricsPublisher != null) {
            metricsPublisher.increment(metric);
        }
    }

    @Override
    public void incrementWithTags(String metric, String... tags) {
        // 调用指标发布器：记录带标签压缩计数，支持多维聚合。
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags(metric, tags);
        }
    }

    @Override
    public void recordSummary(String metric, double value) {
        // 调用指标发布器：记录压缩耗时与比率等摘要指标。
        if (metricsPublisher != null) {
            metricsPublisher.recordSummary(metric, value);
        }
    }
}

