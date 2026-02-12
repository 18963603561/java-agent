package com.example.agent.capabilities.context.compression.infrastructure.telemetry;

import com.example.agent.capabilities.context.compression.application.port.CompressionTelemetryPort;
import com.example.agent.capabilities.context.compression.observability.CompressionExperimentMetricKeys;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricKeys;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricTags;
import com.example.agent.capabilities.context.compression.observability.CompressionObservation;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    @Override
    public void recordObservation(CompressionObservation observation) {
        // 空值守卫：观测对象为空时直接返回，避免上报无意义数据。
        if (metricsPublisher == null || observation == null) {
            return;
        }
        // 统一上报：使用固定指标键并附带阶段、成功、来源等关键标签。
        metricsPublisher.incrementWithTags(
                CompressionMetricKeys.CONTEXT_COMPRESSION_OBSERVATION_TOTAL,
                CompressionMetricTags.STAGE, normalizeTag(observation.getStage()),
                CompressionMetricTags.SUCCESS, normalizeBoolean(observation.getSuccess()),
                CompressionMetricTags.SOURCE, normalizeTag(observation.getSource()),
                CompressionMetricTags.DUAL_TRACK, normalizeBoolean(observation.getDualTrackEnabled()),
                CompressionMetricTags.ROLLOUT_VERSION, normalizeTag(observation.getRolloutVersion()),
                CompressionMetricTags.PRIMARY_SOURCE, normalizeTag(observation.getPrimarySource()),
                CompressionMetricTags.SHADOW_SOURCE, normalizeTag(observation.getShadowSource()),
                CompressionMetricTags.WINNER, normalizeTag(observation.getWinnerSource()),
                CompressionMetricTags.ROLLBACK_REASON, normalizeTag(observation.getRollbackReason()),
                CompressionMetricTags.REASON, normalizeTag(firstNonBlank(
                        observation.getFailureReason(),
                        observation.getTriggerReason(),
                        observation.getShapeReason(),
                        observation.getSummaryInjectReason())),
                CompressionMetricTags.FALLBACK, normalizeBoolean(observation.getFallbackApplied()));
        // 双轨指标：命中双轨时累计实验次数并记录胜出来源分布。
        if (Boolean.TRUE.equals(observation.getDualTrackEnabled())) {
            metricsPublisher.incrementWithTags(
                    CompressionExperimentMetricKeys.CONTEXT_COMPRESSION_DUAL_TRACK_TOTAL,
                    CompressionMetricTags.ROLLOUT_VERSION,
                    normalizeTag(observation.getRolloutVersion()));
            metricsPublisher.incrementWithTags(
                    CompressionExperimentMetricKeys.CONTEXT_COMPRESSION_COMPARISON_TOTAL,
                    CompressionMetricTags.PRIMARY_SOURCE,
                    normalizeTag(observation.getPrimarySource()),
                    CompressionMetricTags.SHADOW_SOURCE,
                    normalizeTag(observation.getShadowSource()));
            metricsPublisher.incrementWithTags(
                    CompressionExperimentMetricKeys.CONTEXT_COMPRESSION_WINNER_TOTAL,
                    CompressionMetricTags.WINNER,
                    normalizeTag(observation.getWinnerSource()));
            // 回滚指标：存在回滚原因时累计回滚计数。
            if (StringUtils.hasText(observation.getRollbackReason())) {
                metricsPublisher.incrementWithTags(
                        CompressionExperimentMetricKeys.CONTEXT_COMPRESSION_ROLLBACK_TOTAL,
                        CompressionMetricTags.ROLLBACK_REASON,
                        normalizeTag(observation.getRollbackReason()));
            }
        }
    }

    /**
     * 规范化标签值。
     */
    private String normalizeTag(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }

    /**
     * 规范化布尔标签。
     */
    private String normalizeBoolean(Boolean value) {
        if (value == null) {
            return "unknown";
        }
        return String.valueOf(value);
    }

    /**
     * 返回首个非空白字符串。
     */
    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        // 循环匹配：按优先级返回首个非空标签，保障原因口径稳定。
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
