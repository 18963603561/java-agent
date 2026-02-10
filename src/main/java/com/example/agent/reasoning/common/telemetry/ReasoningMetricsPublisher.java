package com.example.agent.reasoning.common.telemetry;

import com.example.agent.streaming.observability.MetricsPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理指标发布器。
 *
 * <p>用途：统一推理层指标命名与标签规范，降低策略间指标口径差异。
 */
@Component
public class ReasoningMetricsPublisher {

    private final MetricsPublisher metricsPublisher;

    /**
     * 构造推理指标发布器。
     *
     * @param metricsPublisher 指标发布器
     */
    public ReasoningMetricsPublisher(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 记录推理解析结果指标。
     *
     * @param strategyType 策略类型
     * @param parseSuccess 解析是否成功
     * @param parseErrorType 解析错误类型
     */
    public void recordParseResult(String strategyType, boolean parseSuccess, String parseErrorType) {
        String safeStrategyType = normalizeStrategyType(strategyType);
        metricsPublisher.incrementWithTags(
                "reasoning_parse_total",
                "strategy", safeStrategyType,
                "parseSuccess", String.valueOf(parseSuccess),
                "parseErrorType", normalizeErrorType(parseErrorType)
        );
    }

    /**
     * 记录推理修复结果指标。
     *
     * @param strategyType 策略类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     */
    public void recordRepairResult(String strategyType, boolean repairAttempted, boolean repairSuccess) {
        if (!repairAttempted) {
            return;
        }
        String safeStrategyType = normalizeStrategyType(strategyType);
        metricsPublisher.incrementWithTags(
                "reasoning_repair_total",
                "strategy", safeStrategyType,
                "repairSuccess", String.valueOf(repairSuccess)
        );
    }

    /**
     * 记录推理耗时指标。
     *
     * @param strategyType 策略类型
     * @param millis 耗时毫秒
     */
    public void recordDuration(String strategyType, long millis) {
        String safeStrategyType = normalizeStrategyType(strategyType);
        metricsPublisher.recordSummary("reasoning_duration_ms", millis, "strategy", safeStrategyType);
    }

    private String normalizeStrategyType(String strategyType) {
        if (!StringUtils.hasText(strategyType)) {
            return "unknown";
        }
        return strategyType.trim().toLowerCase();
    }

    private String normalizeErrorType(String parseErrorType) {
        if (!StringUtils.hasText(parseErrorType)) {
            return "none";
        }
        return parseErrorType.trim().toLowerCase();
    }
}

