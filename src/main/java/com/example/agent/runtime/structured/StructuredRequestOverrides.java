package com.example.agent.runtime.structured;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 结构化结果请求级覆盖配置。
 *
 * <p>用途：从步骤输入中解析结构化开关，仅作用于当前请求。</p>
 * <p>输入：步骤输入映射或嵌套 {@code context} 映射。</p>
 * <p>输出：结构化覆盖配置对象。</p>
 */
public final class StructuredRequestOverrides {

    /**
     * 是否启用结构化结果。
     */
    private final Boolean enabled;

    private StructuredRequestOverrides(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 从步骤输入映射解析覆盖配置。
     *
     * @param stepInput 步骤输入映射
     * @return 覆盖配置或 {@code null}
     */
    public static StructuredRequestOverrides fromStepInput(Map<String, Object> stepInput) {
        // 解析覆盖配置映射。
        Map<String, Object> overrides = resolveOverrides(stepInput);
        // 判断覆盖映射是否为空，空时直接返回 null。
        if (overrides == null || overrides.isEmpty()) {
            // 返回空对象，避免空映射误判为覆盖。
            return null;
        }
        // 解析启用开关。
        Boolean enabled = resolveBoolean(overrides.get("enabled"));
        // 判断启用开关是否为空，空时返回 null。
        if (enabled == null) {
            // 返回空对象，表示未提供覆盖。
            return null;
        }
        // 构建覆盖配置对象并返回。
        return new StructuredRequestOverrides(enabled);
    }

    public Boolean getEnabled() {
        return enabled;
    }

    private static Map<String, Object> resolveOverrides(Map<String, Object> stepInput) {
        // 设计意图：优先读取顶层覆盖，其次回退到 context，避免层级冲突。
        // 判断步骤输入是否为空，空时直接返回 null。
        if (stepInput == null || stepInput.isEmpty()) {
            // 返回空对象，避免空映射误判。
            return null;
        }
        // 解析顶层 structured 覆盖映射。
        Map<String, Object> direct = toStringKeyMap(stepInput.get("structured"));
        // 判断顶层覆盖是否有效，命中则直接返回。
        if (direct != null && !direct.isEmpty()) {
            // 返回顶层覆盖映射。
            return direct;
        }
        // 解析顶层 structuredOverrides 覆盖映射。
        direct = toStringKeyMap(stepInput.get("structuredOverrides"));
        // 判断顶层覆盖是否有效，命中则直接返回。
        if (direct != null && !direct.isEmpty()) {
            // 返回顶层覆盖映射。
            return direct;
        }
        // 解析内层 context 映射。
        Map<String, Object> context = toStringKeyMap(stepInput.get("context"));
        // 判断 context 是否为空，空时直接返回 null。
        if (context == null || context.isEmpty()) {
            // 返回空对象，避免空映射误判。
            return null;
        }
        // 解析 context.structured 覆盖映射。
        Map<String, Object> nested = toStringKeyMap(context.get("structured"));
        // 判断覆盖是否有效，命中则直接返回。
        if (nested != null && !nested.isEmpty()) {
            // 返回内层覆盖映射。
            return nested;
        }
        // 解析 context.structuredOverrides 覆盖映射。
        return toStringKeyMap(context.get("structuredOverrides"));
    }

    private static Map<String, Object> toStringKeyMap(Object value) {
        // 判断值是否为映射且非空，非映射时直接返回 null。
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            // 返回空对象，避免无效覆盖。
            return null;
        }
        // 初始化映射副本，隔离外部修改。
        Map<String, Object> copied = new LinkedHashMap<>();
        // 遍历映射条目，统一键为字符串。
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // 写入条目键值，保持原始值。
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        // 返回转换后的映射。
        return copied;
    }

    private static Boolean resolveBoolean(Object value) {
        // 判断值是否为布尔类型，命中则直接返回。
        if (value instanceof Boolean boolValue) {
            // 返回布尔值。
            return boolValue;
        }
        // 判断值是否为字符串，字符串时尝试解析。
        if (value instanceof String text) {
            // 去除字符串首尾空白，统一解析口径。
            String trimmed = text.trim();
            // 判断字符串是否为空，空时返回 null。
            if (!StringUtils.hasText(trimmed)) {
                // 返回空对象，表示未提供覆盖。
                return null;
            }
            // 返回布尔解析结果。
            return Boolean.parseBoolean(trimmed);
        }
        // 返回空对象，表示无法解析。
        return null;
    }
}
