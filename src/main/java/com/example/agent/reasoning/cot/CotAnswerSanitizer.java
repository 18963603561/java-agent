package com.example.agent.reasoning.cot;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * COT 答案清洗器。
 *
 * <p>用途：移除模型输出中的推理痕迹，保留最终可展示答案。
 */
@Component
public class CotAnswerSanitizer {

    private static final List<String> FINAL_ANSWER_MARKERS = List.of(
            "final answer",
            "answer:",
            "final:",
            "最终答案",
            "最终结论",
            "结论:",
            "结论：",
            "答案:",
            "答案："
    );

    private static final List<String> REASONING_MARKERS = List.of(
            "chain-of-thought",
            "chain of thought",
            "reasoning",
            "thoughts",
            "analysis",
            "let's think step by step",
            "step by step",
            "step 1",
            "step 2",
            "step 3",
            "步骤1",
            "步骤2",
            "步骤3",
            "步骤一",
            "步骤二",
            "步骤三",
            "思维链",
            "推理",
            "思考过程"
    );

    /**
     * 清洗最终答案。
     *
     * @param raw 原始答案
     * @param maxChars 最大长度
     * @return 清洗后的答案
     */
    public String sanitize(String raw, int maxChars) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim();
        String extracted = extractAfterFinalMarker(value);
        if (StringUtils.hasText(extracted)) {
            value = extracted;
        }
        value = removeReasoningLines(value);
        if (!StringUtils.hasText(value) && containsReasoningMarker(raw)) {
            value = extractTailSentence(raw);
        }
        int safeMaxChars = Math.max(0, maxChars);
        if (safeMaxChars > 0 && value.length() > safeMaxChars) {
            value = value.substring(0, safeMaxChars);
        }
        return value.trim();
    }

    private String extractAfterFinalMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : FINAL_ANSWER_MARKERS) {
            int index = lower.indexOf(marker);
            if (index < 0) {
                continue;
            }
            int start = index + marker.length();
            String candidate = value.substring(start).trim();
            if (candidate.startsWith(":") || candidate.startsWith("：")) {
                candidate = candidate.substring(1).trim();
            }
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String removeReasoningLines(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String[] lines = value.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (isReasoningLine(trimmed)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(trimmed);
        }
        return builder.toString().trim();
    }

    private boolean isReasoningLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : REASONING_MARKERS) {
            if (lower.startsWith(marker)) {
                return true;
            }
            if (lower.contains(marker + ":") || lower.contains(marker + "：")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsReasoningMarker(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : REASONING_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String extractTailSentence(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String[] parts = value.split("[。.!?\\n]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i] == null ? "" : parts[i].trim();
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return value.trim();
    }
}

