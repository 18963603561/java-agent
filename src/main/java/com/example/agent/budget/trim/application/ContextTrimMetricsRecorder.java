package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.model.ContextTrimStats;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 裁剪指标记录器，统一输出裁剪过程指标。
 */
public class ContextTrimMetricsRecorder {

    private final MetricsPublisher metricsPublisher;

    public ContextTrimMetricsRecorder(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 记录裁剪指标。
     *
     * @param totalBefore 裁剪前令牌
     * @param totalAfter 裁剪后令牌
     * @param removedBySection 分段移除统计
     * @param reasons 超预算原因
     */
    public void record(int totalBefore,
                       int totalAfter,
                       Map<ContextSection, ContextTrimStats> removedBySection,
                       List<String> reasons) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.increment("context_trim_runs_total");
        metricsPublisher.recordSummary("context_trim_before_tokens", totalBefore);
        metricsPublisher.recordSummary("context_trim_after_tokens", totalAfter);
        if (removedBySection != null) {
            for (Map.Entry<ContextSection, ContextTrimStats> entry : removedBySection.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                int removedCount = entry.getValue().getRemovedCount();
                if (removedCount <= 0) {
                    continue;
                }
                String section = entry.getKey().name().toLowerCase(Locale.ROOT);
                metricsPublisher.incrementWithTags("context_trim_removed_items_total", removedCount, "section", section);
            }
        }
        if (reasons != null) {
            for (String reason : reasons) {
                if (reason == null || reason.isBlank()) {
                    continue;
                }
                metricsPublisher.incrementWithTags("context_trim_over_budget_total", "reason", reason);
            }
        }
    }
}
