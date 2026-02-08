package com.example.agent.capabilities.llm.prompt;

import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;

/**
 * 提示词裁剪指标记录器。
 *
 * <p>用途：统一记录提示词裁剪相关指标，避免组装器中混入指标细节。
 */
class PromptTrimMetricsRecorder {

    private final MetricsPublisher metricsPublisher;

    PromptTrimMetricsRecorder(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 记录裁剪指标。
     *
     * @param beforeTokens 裁剪前 token
     * @param afterTokens 裁剪后 token
     * @param truncatedSections 被裁剪段落
     */
    void recordTrimMetrics(int beforeTokens,
                           int afterTokens,
                           List<String> truncatedSections) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.increment("prompt_trim_runs_total");
        metricsPublisher.recordSummary("prompt_trim_before_tokens", beforeTokens);
        metricsPublisher.recordSummary("prompt_trim_after_tokens", afterTokens);
        if (truncatedSections != null) {
            for (String section : truncatedSections) {
                if (section == null || section.isBlank()) {
                    continue;
                }
                metricsPublisher.incrementWithTags("prompt_truncated_sections_total", "section", section);
            }
        }
    }
}

