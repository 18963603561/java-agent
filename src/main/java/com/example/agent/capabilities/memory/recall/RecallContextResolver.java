package com.example.agent.capabilities.memory.recall;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.MemoryRecallProperties;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 召回上下文解析器，负责读取上下文覆盖参数并生成召回快照。
 */
@Component
public class RecallContextResolver {

    public static final String CONTEXT_RECALL_ENABLED = "memoryRecallEnabled";
    public static final String CONTEXT_RECALL_FORCE = "memoryRecallForce";
    public static final String CONTEXT_RECALL_LIMIT = "memoryRecallLimit";
    public static final String CONTEXT_RECALL_MIN_QUERY_LENGTH = "memoryRecallMinQueryLength";
    public static final String CONTEXT_RECALL_INCLUDE_COMPRESSED = "memoryRecallIncludeCompressed";
    public static final String CONTEXT_RECALL_MAX_SUMMARY_CHARS = "memoryRecallMaxSummaryChars";
    public static final String CONTEXT_RECALL_MAX_RECORD_CHARS = "memoryRecallMaxRecordChars";

    private final MemoryRecallProperties properties;

    public RecallContextResolver(MemoryRecallProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析召回上下文。
     *
     * @param request 任务请求
     * @param context 外部传入上下文覆盖
     * @return 召回上下文快照
     */
    public RecallContext resolve(TaskRequest request, Map<String, Object> context) {
        Map<String, Object> effectiveContext = context != null
                ? context
                : request != null ? request.getContext() : null;
        String workflowId = readString(effectiveContext, "workflowId");
        boolean enabled = resolveBoolean(effectiveContext, CONTEXT_RECALL_ENABLED, properties.isEnabled());
        boolean force = resolveBoolean(effectiveContext, CONTEXT_RECALL_FORCE, false);
        int minQueryLength = resolveInt(effectiveContext, CONTEXT_RECALL_MIN_QUERY_LENGTH,
                properties.getMinQueryLength());
        int limit = resolveInt(effectiveContext, CONTEXT_RECALL_LIMIT, properties.getLimit());
        int maxSummaryChars = resolveInt(effectiveContext, CONTEXT_RECALL_MAX_SUMMARY_CHARS,
                properties.getMaxSummaryChars());
        int maxRecordChars = resolveInt(effectiveContext, CONTEXT_RECALL_MAX_RECORD_CHARS,
                properties.getMaxRecordChars());
        boolean includeCompressed = resolveBoolean(effectiveContext, CONTEXT_RECALL_INCLUDE_COMPRESSED,
                properties.isIncludeCompressed());
        return new RecallContext(effectiveContext, workflowId, enabled, force, minQueryLength,
                limit, maxSummaryChars, maxRecordChars, includeCompressed);
    }

    /**
     * 解析布尔参数。
     */
    private boolean resolveBoolean(Map<String, Object> context, String key, boolean defaultValue) {
        if (context == null || !context.containsKey(key)) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    /**
     * 解析整型参数。
     */
    private int resolveInt(Map<String, Object> context, String key, int defaultValue) {
        if (context == null || !context.containsKey(key)) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 读取字符串参数。
     */
    private String readString(Map<String, Object> context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }
}

