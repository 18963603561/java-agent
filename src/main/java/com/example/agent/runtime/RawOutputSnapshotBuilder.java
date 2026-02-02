package com.example.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 受控原始输出构建器，用于在禁用摘要时生成可控原始结果快照。
 */
@Component
public class RawOutputSnapshotBuilder {

    /**
     * 原始输出快照键名。
     */
    public static final String RAW_OUTPUT_KEY = "rawOutputSnapshot";
    /**
     * 原始输出文本字段名。
     */
    public static final String RAW_OUTPUT_TEXT_KEY = "text";
    /**
     * 原始输出字段列表字段名。
     */
    public static final String RAW_OUTPUT_KEYS_KEY = "keys";
    /**
     * 原始输出字段数量字段名。
     */
    public static final String RAW_OUTPUT_KEY_COUNT_KEY = "keyCount";
    /**
     * 原始输出是否截断字段名。
     */
    public static final String RAW_OUTPUT_TRUNCATED_KEY = "truncated";

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(RawOutputSnapshotBuilder.class);

    /**
     * 原始输出配置。
     */
    private final RawOutputProperties properties;

    /**
     * 构造方法，注入原始输出配置。
     *
     * @param properties 原始输出配置
     */
    public RawOutputSnapshotBuilder(RawOutputProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断是否启用受控原始输出。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnable();
    }

    /**
     * 构建原始输出快照，仅包含受控文本与元信息。
     *
     * @param output 原始输出
     * @return 原始输出快照
     */
    public Map<String, Object> build(Object output) {
        if (!isEnabled() || output == null) {
            return Collections.emptyMap();
        }
        RawOutputLimits limits = RawOutputLimits.from(properties);
        TruncationState truncation = new TruncationState();
        SnapshotWriter writer = new SnapshotWriter(limits.maxChars, truncation);
        Set<String> allowKeys = normalizeKeys(properties != null ? properties.getAllowKeys() : null);
        Set<String> maskKeys = normalizeKeys(properties != null ? properties.getMaskKeys() : null);
        List<String> keys = new ArrayList<>();
        int keyCount = 0;
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();

        if (output instanceof Map<?, ?> map) {
            writer.append("{");
            int index = 0;
            int maxItems = limits.maxListItems;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (writer.isStopped()) {
                    break;
                }
                String keyText = entry.getKey() == null ? "null" : safeToString(entry.getKey());
                String normalizedKey = normalizeKey(keyText);
                if (!allowKeys.isEmpty() && !allowKeys.contains(normalizedKey)) {
                    continue;
                }
                if (maxItems > 0 && index >= maxItems) {
                    truncation.markTruncated();
                    break;
                }
                if (index > 0) {
                    writer.append(",");
                }
                String trimmedKey = trimText(keyText, limits.maxFieldChars, truncation);
                writer.append(trimmedKey);
                writer.append(":");
                if (maskKeys.contains(normalizedKey)) {
                    writer.append("<masked>");
                } else {
                    appendValue(entry.getValue(), writer, limits, truncation, visited, maskKeys);
                }
                keys.add(trimmedKey);
                keyCount++;
                index++;
            }
            writer.append("}");
        } else {
            appendValue(output, writer, limits, truncation, visited, maskKeys);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(RAW_OUTPUT_TEXT_KEY, writer.toString());
        result.put(RAW_OUTPUT_TRUNCATED_KEY, truncation.truncated);
        if (keyCount > 0) {
            result.put(RAW_OUTPUT_KEY_COUNT_KEY, keyCount);
            result.put(RAW_OUTPUT_KEYS_KEY, keys);
        }
        return result;
    }

    /**
     * 从原始输出快照中解析可用文本。
     *
     * @param rawOutput 原始输出快照或文本
     * @return 文本内容
     */
    public static String resolveText(Object rawOutput) {
        if (rawOutput == null) {
            return null;
        }
        if (rawOutput instanceof String text) {
            return text;
        }
        if (rawOutput instanceof Map<?, ?> map) {
            Object text = map.get(RAW_OUTPUT_TEXT_KEY);
            if (text != null && StringUtils.hasText(text.toString())) {
                return text.toString();
            }
            return null;
        }
        return String.valueOf(rawOutput);
    }

    private void appendValue(Object value,
                             SnapshotWriter writer,
                             RawOutputLimits limits,
                             TruncationState truncation,
                             IdentityHashMap<Object, Boolean> visited,
                             Set<String> maskKeys) {
        if (writer.isStopped()) {
            return;
        }
        if (value == null) {
            writer.append("null");
            return;
        }
        if (value instanceof String text) {
            writer.append(trimText(text, limits.maxFieldChars, truncation));
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            writer.append(trimText(safeToString(value), limits.maxFieldChars, truncation));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(map, writer, limits, truncation, visited, maskKeys);
            return;
        }
        if (value instanceof List<?> list) {
            appendList(list, writer, limits, truncation, visited, maskKeys);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(value, writer, limits, truncation, visited, maskKeys);
            return;
        }
        writer.append(trimText(safeToString(value), limits.maxFieldChars, truncation));
    }

    private void appendMap(Map<?, ?> map,
                           SnapshotWriter writer,
                           RawOutputLimits limits,
                           TruncationState truncation,
                           IdentityHashMap<Object, Boolean> visited,
                           Set<String> maskKeys) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(map, Boolean.TRUE) != null) {
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("{");
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            String keyText = entry.getKey() == null ? "null" : safeToString(entry.getKey());
            String normalizedKey = normalizeKey(keyText);
            writer.append(trimText(keyText, limits.maxFieldChars, truncation));
            writer.append(":");
            if (maskKeys.contains(normalizedKey)) {
                writer.append("<masked>");
            } else {
                appendValue(entry.getValue(), writer, limits, truncation, visited, maskKeys);
            }
            index++;
        }
        writer.append("}");
        visited.remove(map);
    }

    private void appendList(List<?> list,
                            SnapshotWriter writer,
                            RawOutputLimits limits,
                            TruncationState truncation,
                            IdentityHashMap<Object, Boolean> visited,
                            Set<String> maskKeys) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(list, Boolean.TRUE) != null) {
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Object item : list) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            appendValue(item, writer, limits, truncation, visited, maskKeys);
            index++;
        }
        writer.append("]");
        visited.remove(list);
    }

    private void appendArray(Object array,
                             SnapshotWriter writer,
                             RawOutputLimits limits,
                             TruncationState truncation,
                             IdentityHashMap<Object, Boolean> visited,
                             Set<String> maskKeys) {
        if (writer.isStopped()) {
            return;
        }
        if (visited.put(array, Boolean.TRUE) != null) {
            writer.append("<cycle>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int length = java.lang.reflect.Array.getLength(array);
        int maxItems = limits.maxListItems;
        for (int i = 0; i < length; i++) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && i >= maxItems) {
                truncation.markTruncated();
                break;
            }
            if (i > 0) {
                writer.append(",");
            }
            Object item = java.lang.reflect.Array.get(array, i);
            appendValue(item, writer, limits, truncation, visited, maskKeys);
        }
        writer.append("]");
        visited.remove(array);
    }

    private String trimText(String text, int maxChars, TruncationState truncation) {
        if (text == null) {
            return null;
        }
        if (maxChars > 0 && text.length() > maxChars) {
            truncation.markTruncated();
            return text.substring(0, maxChars);
        }
        return text;
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value);
        // 异常捕获：记录上下文并按当前策略处理。
        } catch (Exception ex) {
            String className = value.getClass().getSimpleName();
            if (!StringUtils.hasText(className)) {
                className = value.getClass().getName();
            }
            if (log.isDebugEnabled()) {
                log.debug("toString 失败, className={}", value.getClass().getName(), ex);
            }
            return "<toString_error:" + className + ">";
        }
    }

    private Set<String> normalizeKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            String normalizedKey = normalizeKey(key);
            if (StringUtils.hasText(normalizedKey)) {
                normalized.add(normalizedKey);
            }
        }
        return normalized;
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private static final class RawOutputLimits {
        private final int maxChars;
        private final int maxListItems;
        private final int maxFieldChars;

        private RawOutputLimits(int maxChars, int maxListItems, int maxFieldChars) {
            this.maxChars = maxChars;
            this.maxListItems = maxListItems;
            this.maxFieldChars = maxFieldChars;
        }

        private static RawOutputLimits from(RawOutputProperties properties) {
            if (properties == null) {
                return new RawOutputLimits(0, 0, 0);
            }
            return new RawOutputLimits(properties.getMaxChars(),
                    properties.getMaxListItems(),
                    properties.getMaxFieldChars());
        }
    }

    private static final class TruncationState {
        private boolean truncated;

        private void markTruncated() {
            this.truncated = true;
        }
    }

    private static final class SnapshotWriter {
        private final StringBuilder builder = new StringBuilder();
        private final int maxChars;
        private final TruncationState truncation;
        private boolean stopped;

        private SnapshotWriter(int maxChars, TruncationState truncation) {
            this.maxChars = maxChars;
            this.truncation = truncation;
        }

        private void append(String text) {
            if (stopped || text == null) {
                return;
            }
            if (maxChars > 0) {
                int remaining = maxChars - builder.length();
                if (remaining <= 0) {
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
                if (text.length() > remaining) {
                    builder.append(text, 0, remaining);
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
            }
            builder.append(text);
        }

        private boolean isStopped() {
            return stopped;
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
