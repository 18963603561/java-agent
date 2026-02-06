package com.example.agent.runtime.output;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时输出字段提取器。
 *
 * <p>用途：在少数权威点集中解析 {@code Map<String, Object>} 输出中的关键字段（toolName/rawRef/refs），避免重复实现导致不一致。
 * <p>边界：该类仅做“读取与规范化（trim/空白处理）”，不做裁剪、摘要生成等重量逻辑。
 */
public final class OutputFieldExtractor {

    private OutputFieldExtractor() {
    }

    /**
     * 解析工具名称。
     *
     * <p>约定：优先读取规范字段 {@code toolName}。
     * <p>兼容：当顶层 {@code tool} 为字符串时视为工具名别名；当顶层 {@code tool} 为对象时尝试读取 {@code tool.name}。</p>
     *
     * @param payload 输出映射
     * @return 工具名称或 {@code null}
     */
    public static String resolveToolName(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String toolName = toNonBlankString(payload.get(OutputKeys.TOOL_NAME));
        if (toolName != null) {
            return toolName;
        }
        Object tool = payload.get(OutputKeys.TOOL);
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
     * <p>约定：优先读取顶层 {@code rawRef}，其次依次读取 {@code rawResult.rawRef}、{@code result.rawRef}、{@code raw.rawRef}。</p>
     *
     * @param payload 输出映射
     * @return 原始引用键或 {@code null}
     */
    public static String resolveRawRef(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String direct = toNonBlankString(payload.get(OutputKeys.RAW_REF));
        if (direct != null) {
            return direct;
        }
        String nested = readNestedRawRef(payload.get(OutputKeys.RAW_RESULT));
        if (nested != null) {
            return nested;
        }
        nested = readNestedRawRef(payload.get(OutputKeys.RESULT));
        if (nested != null) {
            return nested;
        }
        return readNestedRawRef(payload.get(OutputKeys.RAW));
    }

    /**
     * 解析引用集合。
     *
     * <p>约定：合并顶层 {@code refs}、顶层各类 *RawRef 字段，以及 {@code raw.refs}。</p>
     *
     * @param payload 输出映射
     * @return 引用集合（不可变视图）
     */
    public static Map<String, String> resolveRefs(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, String> refs = new LinkedHashMap<>();
        mergeRefs(refs, payload.get(OutputKeys.REFS));
        mergeRefValue(refs, OutputKeys.DECISION_RAW_REF, payload.get(OutputKeys.DECISION_RAW_REF));
        mergeRefValue(refs, OutputKeys.SUMMARY_RAW_REF, payload.get(OutputKeys.SUMMARY_RAW_REF));
        mergeRefValue(refs, OutputKeys.TOOL_RAW_REF, payload.get(OutputKeys.TOOL_RAW_REF));
        mergeRefValue(refs, OutputKeys.MODEL_RAW_REF, payload.get(OutputKeys.MODEL_RAW_REF));
        if (payload.get(OutputKeys.RAW) instanceof Map<?, ?> rawMap) {
            mergeRefs(refs, rawMap.get(OutputKeys.REFS));
        }
        return refs.isEmpty() ? Map.of() : Collections.unmodifiableMap(refs);
    }

    private static String readNestedRawRef(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        return toNonBlankString(map.get(OutputKeys.RAW_REF));
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
