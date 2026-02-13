package com.example.agent.runtime.structured;

import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 结构化结果观测记录器。
 *
 * <p>用途：统一记录结构化校验失败、缺失字段与降级指标。</p>
 */
@Component
public class StructuredMetricsRecorder {

    /**
     * 指标名前缀。
     */
    private static final String METRIC_PREFIX = "agent.structured";

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 观测配置。
     */
    private final StructuredObserveProperties observeProperties;

    public StructuredMetricsRecorder(ObjectProvider<MetricsPublisher> metricsPublisherProvider,
                                     StructuredObserveProperties observeProperties) {
        // 获取指标发布器，避免强依赖导致启动失败。
        this.metricsPublisher = metricsPublisherProvider == null ? null : metricsPublisherProvider.getIfAvailable();
        // 绑定观测配置，供开关与采样使用。
        this.observeProperties = observeProperties;
    }

    /**
     * 判断是否允许记录指标。
     *
     * @return true 表示允许记录
     */
    public boolean isEnabled() {
        // 判断观测配置是否启用并返回结果。
        return observeProperties != null && observeProperties.isEnable();
    }

    /**
     * 判断是否需要记录日志。
     *
     * @return true 表示需要记录
     */
    public boolean shouldLog() {
        // 判断是否启用观测，未启用时直接返回否。
        if (!isEnabled()) {
            return false;
        }
        // 读取日志采样比例。
        double rate = observeProperties.getLogSampleRate();
        // 根据采样比例判断是否记录日志。
        return shouldSample(rate);
    }

    /**
     * 记录结构化校验失败指标。
     *
     * @param kind 结果类型
     * @param schemaVersion 结构版本
     */
    public void recordValidationFailed(ResultKind kind, Integer schemaVersion) {
        // 判断观测是否启用，未启用时直接返回。
        if (!isEnabled()) {
            return;
        }
        // 判断指标发布器是否为空，空时直接返回。
        if (metricsPublisher == null) {
            return;
        }
        // 判断是否命中采样比例，未命中时直接返回。
        if (!shouldSample(observeProperties.getSampleRate())) {
            return;
        }
        // 解析类型标签值。
        String kindTag = resolveKindTag(kind);
        // 解析版本标签值。
        String versionTag = resolveSchemaTag(schemaVersion);
        // 记录结构化校验失败计数指标。
        metricsPublisher.incrementWithTags(metricName("validation.failed"),
                "kind", kindTag,
                "schemaVersion", versionTag);
    }

    /**
     * 记录结构化降级指标。
     *
     * @param kind 结果类型
     * @param schemaVersion 结构版本
     */
    public void recordDegrade(ResultKind kind, Integer schemaVersion) {
        // 判断观测是否启用，未启用时直接返回。
        if (!isEnabled()) {
            return;
        }
        // 判断指标发布器是否为空，空时直接返回。
        if (metricsPublisher == null) {
            return;
        }
        // 判断是否命中采样比例，未命中时直接返回。
        if (!shouldSample(observeProperties.getSampleRate())) {
            return;
        }
        // 解析类型标签值。
        String kindTag = resolveKindTag(kind);
        // 解析版本标签值。
        String versionTag = resolveSchemaTag(schemaVersion);
        // 记录结构化降级计数指标。
        metricsPublisher.incrementWithTags(metricName("degrade"),
                "kind", kindTag,
                "schemaVersion", versionTag);
    }

    /**
     * 记录缺失字段数量指标。
     *
     * @param kind 结果类型
     * @param schemaVersion 结构版本
     * @param missingCount 缺失数量
     */
    public void recordMissingFields(ResultKind kind, Integer schemaVersion, int missingCount) {
        // 判断观测是否启用，未启用时直接返回。
        if (!isEnabled()) {
            return;
        }
        // 判断指标发布器是否为空，空时直接返回。
        if (metricsPublisher == null) {
            return;
        }
        // 判断缺失数量是否有效，非正数时直接返回。
        if (missingCount <= 0) {
            return;
        }
        // 判断是否命中采样比例，未命中时直接返回。
        if (!shouldSample(observeProperties.getSampleRate())) {
            return;
        }
        // 解析类型标签值。
        String kindTag = resolveKindTag(kind);
        // 解析版本标签值。
        String versionTag = resolveSchemaTag(schemaVersion);
        // 记录缺失字段数量分布指标。
        metricsPublisher.recordSummary(metricName("missing_fields"), missingCount,
                "kind", kindTag,
                "schemaVersion", versionTag);
    }

    private boolean shouldSample(double rate) {
        // 判断采样比例是否小于等于 0，低于阈值时直接返回否。
        if (rate <= 0.0D) {
            return false;
        }
        // 判断采样比例是否大于等于 1，满足时直接返回是。
        if (rate >= 1.0D) {
            return true;
        }
        // 生成随机值用于采样判断。
        double value = ThreadLocalRandom.current().nextDouble();
        // 返回采样命中结果。
        return value < rate;
    }

    private String resolveKindTag(ResultKind kind) {
        // 判断类型是否为空，空时返回 unknown。
        if (kind == null) {
            return "unknown";
        }
        // 返回类型标签值。
        return kind.name();
    }

    private String resolveSchemaTag(Integer schemaVersion) {
        // 判断版本是否为空，空时返回 unknown。
        if (schemaVersion == null) {
            return "unknown";
        }
        // 返回版本标签值。
        return String.valueOf(schemaVersion);
    }

    private String metricName(String suffix) {
        // 判断后缀是否为空，空时返回前缀本身。
        if (!StringUtils.hasText(suffix)) {
            return METRIC_PREFIX;
        }
        // 返回拼接后的指标名称。
        return METRIC_PREFIX + "." + suffix;
    }
}
