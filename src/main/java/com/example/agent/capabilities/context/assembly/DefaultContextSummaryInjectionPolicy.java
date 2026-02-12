package com.example.agent.capabilities.context.assembly;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricKeys;
import com.example.agent.capabilities.context.compression.observability.CompressionMetricTags;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认上下文摘要注入策略。
 *
 * <p>用途：将压缩摘要以结构化片段注入 developer 文本，形成统一 context_summary 装配入口。</p>
 */
@Component
public class DefaultContextSummaryInjectionPolicy implements ContextSummaryInjectionPolicy {

    private final ContextCompressionProperties properties;
    private final MetricsPublisher metricsPublisher;

    public DefaultContextSummaryInjectionPolicy(ContextCompressionProperties properties,
                                                MetricsPublisher metricsPublisher) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public void inject(PromptAssemblyInput input,
                       ContextSnapshot snapshot,
                       ContextCompressionResult compressionResult) {
        // 空值守卫：缺少基础入参时终止注入并标记原因。
        if (input == null || snapshot == null || compressionResult == null) {
            markInjection(compressionResult, false, "INJECTION_INPUT_MISSING");
            return;
        }
        // 配置判定：注入开关关闭时不执行摘要拼装。
        if (!isInjectionEnabled()) {
            markInjection(compressionResult, false, "INJECTION_DISABLED");
            return;
        }
        WorkingMemory workingMemory = snapshot.getWorkingMemory();
        String summary = workingMemory != null ? workingMemory.getSummary() : null;
        // 内容判定：摘要为空时跳过注入，避免污染提示文本。
        if (!StringUtils.hasText(summary)) {
            markInjection(compressionResult, false, "SUMMARY_EMPTY");
            return;
        }

        String normalized = summary.trim();
        int maxChars = resolveMaxChars();
        // 长度治理：摘要超长时执行截断，控制装配体积与预算波动。
        if (maxChars > 0 && normalized.length() > maxChars) {
            normalized = normalized.substring(0, maxChars);
        }
        String segment = "[context_summary]\n" + normalized;
        String currentDeveloper = input.getDeveloperText();
        String merged = StringUtils.hasText(currentDeveloper)
                ? currentDeveloper + "\n\n" + segment
                : segment;
        input.setDeveloperText(merged);
        input.setContextSummaryText(normalized);

        markInjection(compressionResult, true, "SUMMARY_INJECTED");
        recordInjectionMetric("true", "SUMMARY_INJECTED");
    }

    /**
     * 判断是否启用注入。
     */
    private boolean isInjectionEnabled() {
        if (properties == null || properties.getInjection() == null) {
            return true;
        }
        return properties.getInjection().isEnabled();
    }

    /**
     * 解析注入长度上限。
     */
    private int resolveMaxChars() {
        if (properties == null || properties.getInjection() == null) {
            return 800;
        }
        int maxChars = properties.getInjection().getMaxChars();
        if (maxChars <= 0) {
            return 800;
        }
        return maxChars;
    }

    /**
     * 记录注入结果。
     */
    private void markInjection(ContextCompressionResult compressionResult,
                               boolean injected,
                               String reason) {
        // 结果回填：将注入结果写入压缩结果对象，供装配与观测统一消费。
        if (compressionResult != null) {
            compressionResult.setSummaryInjected(injected);
            compressionResult.setSummaryInjectReason(reason);
        }
        recordInjectionMetric(String.valueOf(injected), reason);
    }

    /**
     * 记录注入指标。
     */
    private void recordInjectionMetric(String injected, String reason) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags(CompressionMetricKeys.CONTEXT_SUMMARY_INJECTION_TOTAL,
                CompressionMetricTags.INJECTED, injected,
                CompressionMetricTags.REASON, StringUtils.hasText(reason) ? reason : "unknown");
    }
}

