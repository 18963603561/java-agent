package com.example.agent.runtime.summary;

import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 语义摘要观测记录器。
 *
 * <p>用途：统一记录语义摘要生成、质量与抽检指标。</p>
 */
@Component
public class SemanticSummaryMetricsRecorder {

    /**
     * 指标名前缀。
     */
    private static final String METRIC_PREFIX = "agent.summary";

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 观测配置。
     */
    private final SemanticSummaryObserveProperties observeProperties;

    public SemanticSummaryMetricsRecorder(ObjectProvider<MetricsPublisher> metricsPublisherProvider,
                                          SemanticSummaryObserveProperties observeProperties) {
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
            // 返回 false，表示不记录日志。
            return false;
        }
        // 读取日志采样比例。
        double rate = observeProperties.getLogSampleRate();
        // 根据采样比例判断是否记录日志。
        return shouldSample(rate);
    }

    /**
     * 记录摘要生成指标。
     *
     * @param input 摘要输入
     * @param scenario 摘要场景
     * @param summary 摘要对象
     * @param durationMs 生成耗时
     */
    public void recordGeneration(StepSummaryBuildInput input,
                                 SemanticSummaryScenario scenario,
                                 SemanticSummary summary,
                                 long durationMs) {
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

        // 解析场景标签值。
        String scenarioTag = resolveScenarioTag(scenario);
        // 解析步骤类型标签值。
        String stepTag = resolveStepTypeTag(input);
        // 解析工具名称标签值。
        String toolTag = resolveToolTag(input);

        // 记录摘要生成计数指标。
        metricsPublisher.incrementWithTags(metricName("generate.total"),
                "scenario", scenarioTag,
                "stepType", stepTag,
                "toolName", toolTag);
        // 记录摘要生成耗时指标。
        metricsPublisher.recordSummary(metricName("generate.duration"), durationMs,
                "scenario", scenarioTag,
                "stepType", stepTag,
                "toolName", toolTag);

        // 判断摘要是否为空，空时记录空摘要指标。
        if (isSummaryEmpty(summary)) {
            // 记录空摘要计数指标。
            metricsPublisher.incrementWithTags(metricName("generate.empty"),
                    "scenario", scenarioTag,
                    "stepType", stepTag,
                    "toolName", toolTag);
        }
        // 判断摘要是否截断，截断时记录截断指标。
        if (summary != null && summary.isTruncated()) {
            // 记录摘要截断计数指标。
            metricsPublisher.incrementWithTags(metricName("generate.truncated"),
                    "scenario", scenarioTag,
                    "stepType", stepTag,
                    "toolName", toolTag);
        }
    }

    /**
     * 记录摘要质量指标。
     *
     * @param scenario 摘要场景
     * @param quality 质量结果
     */
    public void recordQuality(SemanticSummaryScenario scenario, SemanticSummaryQuality quality) {
        // 判断观测是否启用，未启用时直接返回。
        if (!isEnabled()) {
            return;
        }
        // 判断指标发布器或质量对象是否为空，空时直接返回。
        if (metricsPublisher == null || quality == null) {
            return;
        }
        // 判断是否命中采样比例，未命中时直接返回。
        if (!shouldSample(observeProperties.getSampleRate())) {
            return;
        }
        // 解析场景标签值。
        String scenarioTag = resolveScenarioTag(scenario);

        // 判断评分是否存在，存在时记录评分指标。
        if (quality.getScore() != null) {
            // 记录质量评分分布指标。
            metricsPublisher.recordSummary(metricName("quality.score"), quality.getScore(),
                    "scenario", scenarioTag);
        }
        // 判断覆盖度是否存在，存在时记录覆盖度指标。
        if (quality.getCoverage() != null) {
            // 记录覆盖度分布指标。
            metricsPublisher.recordSummary(metricName("quality.coverage"), quality.getCoverage(),
                    "scenario", scenarioTag);
        }
        // 判断一致性是否存在，存在时记录一致性指标。
        if (quality.getCoherence() != null) {
            // 记录一致性分布指标。
            metricsPublisher.recordSummary(metricName("quality.coherence"), quality.getCoherence(),
                    "scenario", scenarioTag);
        }
        // 判断是否存在低质量告警，存在时记录低质量计数。
        if (containsWarning(quality.getWarnings(), "score_below_threshold")) {
            // 记录低质量摘要计数指标。
            metricsPublisher.incrementWithTags(metricName("quality.low_score_total"),
                    "scenario", scenarioTag);
        }
    }

    /**
     * 记录抽检命中指标。
     *
     * @param scenario 摘要场景
     * @param reason 抽检原因
     */
    public void recordAuditSample(SemanticSummaryScenario scenario, String reason) {
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
        // 解析场景标签值。
        String scenarioTag = resolveScenarioTag(scenario);
        // 解析原因标签值。
        String reasonTag = StringUtils.hasText(reason) ? reason : "sample_rate";
        // 记录抽检命中计数指标。
        metricsPublisher.incrementWithTags(metricName("audit.sampled_total"),
                "scenario", scenarioTag,
                "reason", reasonTag);
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

    private String resolveScenarioTag(SemanticSummaryScenario scenario) {
        // 判断场景是否为空或编码为空，空时返回默认标签。
        if (scenario == null || !StringUtils.hasText(scenario.getCode())) {
            return "default";
        }
        // 返回场景编码标签。
        return scenario.getCode();
    }

    private String resolveStepTypeTag(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时返回 unknown。
        if (input == null) {
            return "unknown";
        }
        // 读取步骤类型。
        String stepType = input.getStepType();
        // 返回步骤类型标签。
        return StringUtils.hasText(stepType) ? stepType : "unknown";
    }

    private String resolveToolTag(StepSummaryBuildInput input) {
        // 判断输入是否为空，空时返回 unknown。
        if (input == null) {
            return "unknown";
        }
        // 读取工具名称。
        String toolName = input.getToolName();
        // 返回工具名称标签。
        return StringUtils.hasText(toolName) ? toolName : "unknown";
    }

    private boolean isSummaryEmpty(SemanticSummary summary) {
        // 判断摘要对象是否为空，空时视为摘要为空。
        if (summary == null) {
            return true;
        }
        // 读取摘要文本。
        String text = summary.getText();
        // 判断摘要文本是否为空，空时视为摘要为空。
        if (!StringUtils.hasText(text)) {
            return true;
        }
        // 标准化摘要文本用于占位判断。
        String normalized = text.trim().toLowerCase();
        // 返回是否为占位文本判断结果。
        return "no_summary".equals(normalized) || "(summary disabled)".equals(normalized);
    }

    private boolean containsWarning(List<String> warnings, String expected) {
        // 判断告警列表或关键字是否为空，空时返回否。
        if (warnings == null || warnings.isEmpty() || !StringUtils.hasText(expected)) {
            return false;
        }
        // 循环遍历告警列表，判断是否命中关键字。
        for (String warning : warnings) {
            // 判断告警是否为空，空时跳过。
            if (warning == null) {
                continue;
            }
            // 判断告警是否匹配，匹配时返回是。
            if (expected.equals(warning)) {
                return true;
            }
        }
        // 返回未命中结果。
        return false;
    }

    private String metricName(String suffix) {
        // 返回拼接后的指标名称。
        return METRIC_PREFIX + "." + suffix;
    }
}
