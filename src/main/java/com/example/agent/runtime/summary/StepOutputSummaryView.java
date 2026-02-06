package com.example.agent.runtime.summary;

import com.example.agent.runtime.output.OutputKeys;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 步骤输出摘要视图。
 *
 * <p>用途：为跨包消费方提供“只读投影”，避免直接在消费方散落 {@code Map.get("xxx")} 的硬编码键访问。
 * <p>输入：{@code StepOutputSummaryBuilder.build(...)} 生成的摘要映射（通常存放于 {@code StepExecutionOutput.summary}）。
 * <p>输出：面向反思提示词等场景的精简上下文。
 * <p>边界：该类只做轻量的字段提取与兜底拼装，不做业务裁剪与复杂推导。
 */
public final class StepOutputSummaryView {

    /**
     * 反思上下文摘要的截断后缀。
     */
    private static final String SUMMARY_TRUNCATED_SUFFIX = "...(truncated)";

    private final Map<String, Object> summaryView;

    private StepOutputSummaryView(Map<String, Object> summaryView) {
        this.summaryView = summaryView == null ? Map.of() : summaryView;
    }

    /**
     * 基于摘要映射创建视图对象。
     *
     * @param summaryView 摘要映射
     * @return 视图对象
     */
    public static StepOutputSummaryView from(Map<String, Object> summaryView) {
        return new StepOutputSummaryView(summaryView);
    }

    /**
     * 创建空视图对象。
     *
     * @return 空视图
     */
    public static StepOutputSummaryView empty() {
        return new StepOutputSummaryView(Map.of());
    }

    /**
     * 构建用于反思提示词的摘要上下文。
     *
     * <p>约定：上下文仅包含 {@code outputSummary} 与可选的 {@code outputDigest}，并保证 {@code outputSummary.summary} 具备兜底文本。
     *
     * @param maxSummaryChars 摘要最大字符数（用于截断）
     * @return 反思上下文
     */
    public Map<String, Object> toReflectionContext(int maxSummaryChars) {
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> outputSummary = copyObjectMap(summaryView.get(OutputKeys.OUTPUT_SUMMARY));
        Map<String, Object> outputDigest = copyObjectMap(summaryView.get(OutputKeys.OUTPUT_DIGEST));

        String summaryText = readSummaryText(outputSummary);
        if (!StringUtils.hasText(summaryText)) {
            summaryText = buildDigestSummary(outputDigest);
            if (!StringUtils.hasText(summaryText)) {
                summaryText = "(summary disabled)";
            }
        }
        outputSummary.put(OutputKeys.SUMMARY, truncateSummary(summaryText, maxSummaryChars));
        context.put(OutputKeys.OUTPUT_SUMMARY, outputSummary);
        if (!outputDigest.isEmpty()) {
            context.put(OutputKeys.OUTPUT_DIGEST, outputDigest);
        }
        return context;
    }

    private String readSummaryText(Map<String, Object> outputSummary) {
        if (outputSummary == null || outputSummary.isEmpty()) {
            return null;
        }
        Object value = outputSummary.get(OutputKeys.SUMMARY);
        return value == null ? null : value.toString();
    }

    private Map<String, Object> copyObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> copied = new HashMap<>();
        map.forEach((k, v) -> copied.put(String.valueOf(k), v));
        return copied;
    }

    private String buildDigestSummary(Map<String, Object> digest) {
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("digest:");
        appendDigestField(builder, OutputKeys.KEY_COUNT, digest.get(OutputKeys.KEY_COUNT));
        appendDigestField(builder, OutputKeys.KEYS, digest.get(OutputKeys.KEYS));
        Object charCount = digest.get(OutputKeys.CHAR_COUNT);
        if (charCount != null) {
            boolean truncated = Boolean.TRUE.equals(digest.get(OutputKeys.TRUNCATED));
            String field = truncated ? OutputKeys.CHAR_COUNT + "<=" : OutputKeys.CHAR_COUNT;
            appendDigestField(builder, field, charCount);
        }
        appendDigestField(builder, OutputKeys.TRUNCATED, digest.get(OutputKeys.TRUNCATED));
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        if (builder == null || value == null) {
            return;
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ':') {
            builder.append(", ");
        } else {
            builder.append(' ');
        }
        builder.append(field).append('=').append(value);
    }

    private String truncateSummary(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= SUMMARY_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, maxChars);
        }
        int endIndex = maxChars - SUMMARY_TRUNCATED_SUFFIX.length();
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + SUMMARY_TRUNCATED_SUFFIX;
    }
}

