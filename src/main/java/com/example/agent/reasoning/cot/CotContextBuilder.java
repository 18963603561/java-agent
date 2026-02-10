package com.example.agent.reasoning.cot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 链式推理上下文构建器。
 *
 * <p>用途：统一构建 COT 提示词与修复流程所需的上下文对象与 JSON 文本。
 */
@Component
public class CotContextBuilder {

    private static final int MAX_MEMORY_CHARS = 800;
    private static final int MAX_OBSERVATION_CHARS = 500;

    private final ObjectMapper objectMapper;

    /**
     * 构造上下文构建器。
     *
     * @param objectMapper JSON 序列化工具
     */
    public CotContextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构建 COT 上下文。
     *
     * @param question 问题文本
     * @param input 步骤输入上下文
     * @param stepSummaries 历史步骤摘要
     * @param stepIndex 当前步骤索引
     * @param maxSteps 最大步骤数
     * @return 上下文对象与 JSON 文本
     */
    public CotContext build(String question,
                            Map<String, Object> input,
                            List<String> stepSummaries,
                            int stepIndex,
                            int maxSteps) {
        Map<String, Object> context = new HashMap<>();
        context.put("question", question);

        String memorySummary = extractMemorySummary(input);
        if (StringUtils.hasText(memorySummary)) {
            context.put("memorySummary", memorySummary);
        }

        String observation = extractObservation(input);
        if (StringUtils.hasText(observation)) {
            context.put("recentObservation", observation);
        }

        if (stepSummaries != null && !stepSummaries.isEmpty()) {
            context.put("previousSteps", stepSummaries);
        }

        context.put("stepIndex", stepIndex);
        context.put("maxSteps", maxSteps);
        return new CotContext(context, serialize(context));
    }

    private String serialize(Map<String, Object> context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String extractMemorySummary(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object memoryObj = input.get("memory");
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Object summary = memoryMap.get("summary");
            if (summary instanceof String text) {
                return truncate(text, MAX_MEMORY_CHARS);
            }
        }
        return null;
    }

    private String extractObservation(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object summary = input.get("observationSummary");
        if (summary == null) {
            summary = input.get("observationsSummary");
        }
        if (summary == null) {
            summary = input.get("lastStepSummary");
        }
        String text = resolveSummaryText(summary);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return truncate(text, MAX_OBSERVATION_CHARS);
    }

    private String resolveSummaryText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            Object summary = map.get("summary");
            if (summary != null && StringUtils.hasText(summary.toString())) {
                return summary.toString();
            }
            Object text = map.get("text");
            if (text != null && StringUtils.hasText(text.toString())) {
                return text.toString();
            }
            Object sample = map.get("sample");
            if (sample != null && StringUtils.hasText(sample.toString())) {
                return sample.toString();
            }
            String status = map.get("status") != null ? map.get("status").toString() : null;
            String errorCode = map.get("errorCode") != null ? map.get("errorCode").toString() : null;
            String tool = map.get("tool") != null ? map.get("tool").toString() : null;
            if (StringUtils.hasText(status) || StringUtils.hasText(errorCode) || StringUtils.hasText(tool)) {
                StringBuilder builder = new StringBuilder();
                if (StringUtils.hasText(status)) {
                    builder.append("status=").append(status);
                }
                if (StringUtils.hasText(errorCode)) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append("errorCode=").append(errorCode);
                }
                if (StringUtils.hasText(tool)) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append("tool=").append(tool);
                }
                return builder.toString();
            }
            return null;
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                String part = resolveSummaryText(item);
                if (StringUtils.hasText(part)) {
                    parts.add(part);
                }
            }
            if (!parts.isEmpty()) {
                return String.join("; ", parts);
            }
            return null;
        }
        return null;
    }

    private String truncate(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * COT 上下文对象。
     */
    public record CotContext(Map<String, Object> context, String contextJson) {
    }
}

