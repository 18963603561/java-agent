package com.example.agent.reasoning.common;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理解析辅助工具。
 *
 * <p>用途：提供常见 JSON 字段安全读取能力，避免策略实现重复代码。
 */
@Component
public class ReasoningParseSupport {

    /**
     * 读取布尔字段。
     */
    public boolean resolveBoolean(Map<String, Object> root, String key, boolean defaultValue) {
        if (root == null || !root.containsKey(key)) {
            return defaultValue;
        }
        Object value = root.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    /**
     * 读取字符串字段，支持备用字段。
     */
    public String resolveString(Map<String, Object> root, String primary, String fallbackKey) {
        if (root == null) {
            return null;
        }
        Object value = root.get(primary);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        if (fallbackKey == null) {
            return null;
        }
        Object fallback = root.get(fallbackKey);
        if (fallback instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    /**
     * 读取浮点字段。
     */
    public Double resolveDouble(Map<String, Object> root, String key) {
        if (root == null || !root.containsKey(key)) {
            return null;
        }
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

