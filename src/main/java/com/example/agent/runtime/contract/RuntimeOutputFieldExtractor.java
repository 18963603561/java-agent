package com.example.agent.runtime.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时输出字段提取器（契约层）。
 *
 * <p>用途：在跨包权威点集中解析输出映射中的关键字段（toolName/rawRef/refs）。
 * <p>边界：该类仅做读取与轻量规范化，不做摘要或业务推导。
 */
public final class RuntimeOutputFieldExtractor {

    private RuntimeOutputFieldExtractor() {
    }

    /**
     * 解析工具名称。
     *
     * @param payload 输出映射
     * @return 工具名称或空
     */
    public static String resolveToolName(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String toolName = toNonBlankString(payload.get(RuntimeOutputKeys.TOOL_NAME));
        if (toolName != null) {
            return toolName;
        }
        Object tool = payload.get(RuntimeOutputKeys.TOOL);
        String legacy = toNonBlankString(tool);
        if (legacy != null) {
            return legacy;
        }
        if (tool instanceof Map<?, ?> toolMap) {
            return toNonBlankString(toolMap.get("name"));
        }
        return null;
    }

    /**
     * 解析原始引用键。
     *
     * @param payload 输出映射
     * @return 原始引用或空
     */
    public static String resolveRawRef(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String direct = toNonBlankString(payload.get(RuntimeOutputKeys.RAW_REF));
        if (direct != null) {
            return direct;
        }
        String nested = readNestedRawRef(payload.get(RuntimeOutputKeys.RAW_RESULT));
        if (nested != null) {
            return nested;
        }
        nested = readNestedRawRef(payload.get(RuntimeOutputKeys.RESULT));
        if (nested != null) {
            return nested;
        }
        return readNestedRawRef(payload.get(RuntimeOutputKeys.RAW));
    }

    /**
     * 解析引用集合。
     *
     * @param payload 输出映射
     * @return 引用集合（不可变）
     */
    public static Map<String, String> resolveRefs(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, String> refs = new LinkedHashMap<>();
        mergeRefs(refs, payload.get(RuntimeOutputKeys.REFS));
        mergeRefValue(refs, RuntimeOutputKeys.DECISION_RAW_REF, payload.get(RuntimeOutputKeys.DECISION_RAW_REF));
        mergeRefValue(refs, RuntimeOutputKeys.SUMMARY_RAW_REF, payload.get(RuntimeOutputKeys.SUMMARY_RAW_REF));
        mergeRefValue(refs, RuntimeOutputKeys.TOOL_RAW_REF, payload.get(RuntimeOutputKeys.TOOL_RAW_REF));
        mergeRefValue(refs, RuntimeOutputKeys.MODEL_RAW_REF, payload.get(RuntimeOutputKeys.MODEL_RAW_REF));
        if (payload.get(RuntimeOutputKeys.RAW) instanceof Map<?, ?> rawMap) {
            mergeRefs(refs, rawMap.get(RuntimeOutputKeys.REFS));
        }
        return refs.isEmpty() ? Map.of() : Collections.unmodifiableMap(refs);
    }

    private static String readNestedRawRef(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        return toNonBlankString(map.get(RuntimeOutputKeys.RAW_REF));
    }

    private static void mergeRefs(Map<String, String> target, Object refsObj) {
        if (target == null || !(refsObj instanceof Map<?, ?> map) || map.isEmpty()) {
            return;
        }
        map.forEach((key, value) -> {
            if (key != null && value != null) {
                target.put(String.valueOf(key), String.valueOf(value));
            }
        });
    }

    private static void mergeRefValue(Map<String, String> target, String key, Object value) {
        if (target == null || key == null) {
            return;
        }
        String text = toNonBlankString(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    private static String toNonBlankString(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

