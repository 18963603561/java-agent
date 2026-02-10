package com.example.agent.reasoning.common;

import com.example.agent.reasoning.thoughttree.ThoughtTreeConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 推理输入上下文。
 *
 * <p>用途：收敛推理执行所需输入字段，避免直接依赖弱类型 Map。
 */
public class ReasoningInput {

    private final Map<String, Object> attributes;

    /**
     * 构造推理输入。
     *
     * @param attributes 原始输入属性
     */
    public ReasoningInput(Map<String, Object> attributes) {
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object get(String key) {
        return attributes.get(key);
    }

    public String getString(String key) {
        Object value = get(key);
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public Integer getInteger(String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public Long getLong(String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public Double getDouble(String key) {
        Object value = get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item == null ? null : item.toString())
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        }
        if (value instanceof String text) {
            return List.of(text.split(",")).stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    public ThoughtTreeConfig getThoughtTreeConfig() {
        Object value = get("reasoning.thoughtTreeConfig");
        if (value instanceof ThoughtTreeConfig thoughtTreeConfig) {
            return thoughtTreeConfig;
        }
        return null;
    }
}

