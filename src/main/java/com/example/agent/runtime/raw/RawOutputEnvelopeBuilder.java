package com.example.agent.runtime.raw;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.example.agent.runtime.summary.StepSummaryProperties;

/**
 * 原始输出封装构建器。
 *
 * <p>用途：为步骤输出生成受控的原始层结构，避免直接透传超大对象导致上下文膨胀。
 * <p>输入：步骤原始输出映射。
 * <p>输出：包含 rawRef、裁剪后 data、truncated 与 refs 的封装结构。
 */
@Component
public class RawOutputEnvelopeBuilder {

    private final StepSummaryProperties properties;

    public RawOutputEnvelopeBuilder(StepSummaryProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建原始输出封装结构。
     *
     * @param rawOutput 步骤原始输出
     * @return 原始输出封装
     */
    public RawOutputEnvelope build(Map<String, Object> rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return RawOutputEnvelope.empty();
        }
        String rawRef = resolveRawRef(rawOutput);
        Map<String, String> refs = resolveRefs(rawOutput);
        if (!isRawEnabled()) {
            return RawOutputEnvelope.of(rawRef, refs, null, false);
        }

        Limits limits = resolveLimits();
        TruncationState truncationState = new TruncationState();
        Object sanitized = sanitizeValue(rawOutput, limits, truncationState, 3, new IdentityHashMap<>());
        Map<String, Object> data = null;
        if (sanitized instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, value) -> copied.put(String.valueOf(key), value));
            data = copied;
        }
        return RawOutputEnvelope.of(rawRef, refs, data, truncationState.truncated);
    }

    /**
     * 解析原始引用键，优先读取顶层 rawRef。
     *
     * @param rawOutput 原始输出
     * @return 原始引用键
     */
    public String resolveRawRef(Map<String, Object> rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return null;
        }
        String direct = readString(rawOutput.get("rawRef"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        if (rawOutput.get("rawResult") instanceof Map<?, ?> rawResultMap) {
            String nested = readString(rawResultMap.get("rawRef"));
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        if (rawOutput.get("result") instanceof Map<?, ?> resultMap) {
            String nested = readString(resultMap.get("rawRef"));
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {
            String nested = readString(rawMap.get("rawRef"));
            if (StringUtils.hasText(nested)) {
                return nested;
            }
        }
        return null;
    }

    /**
     * 解析引用集合，统一输出字符串键值对。
     *
     * @param rawOutput 原始输出
     * @return 引用集合
     */
    public Map<String, String> resolveRefs(Map<String, Object> rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return Map.of();
        }
        Map<String, String> refs = new LinkedHashMap<>();
        mergeRefs(refs, rawOutput.get("refs"));
        mergeRefValue(refs, "decisionRawRef", rawOutput.get("decisionRawRef"));
        mergeRefValue(refs, "summaryRawRef", rawOutput.get("summaryRawRef"));
        mergeRefValue(refs, "toolRawRef", rawOutput.get("toolRawRef"));
        mergeRefValue(refs, "modelRawRef", rawOutput.get("modelRawRef"));
        if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {
            mergeRefs(refs, rawMap.get("refs"));
        }
        return refs.isEmpty() ? Map.of() : refs;
    }

    private boolean isRawEnabled() {
        return properties == null || properties.isRawEnable();
    }

    private Limits resolveLimits() {
        if (properties == null) {
            return new Limits(4000, 1000, 20);
        }
        return new Limits(properties.getRawMaxChars(),
                properties.getRawMaxFieldChars(),
                properties.getRawMaxListItems());
    }

    private Object sanitizeValue(Object value,
                                 Limits limits,
                                 TruncationState truncationState,
                                 int depth,
                                 IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return null;
        }
        if (depth <= 0) {
            return trimText(String.valueOf(value), limits.maxFieldChars, limits, truncationState);
        }
        if (value instanceof String text) {
            return trimText(text, limits.maxFieldChars, limits, truncationState);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, limits, truncationState, depth, visited);
        }
        if (value instanceof List<?> list) {
            return sanitizeList(list, limits, truncationState, depth, visited);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value, limits, truncationState, depth, visited);
        }
        return trimText(String.valueOf(value), limits.maxFieldChars, limits, truncationState);
    }

    private Object sanitizeMap(Map<?, ?> map,
                               Limits limits,
                               TruncationState truncationState,
                               int depth,
                               IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(map, Boolean.TRUE) != null) {
            truncationState.mark();
            return "<circular>";
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (limits.maxListItems > 0 && index >= limits.maxListItems) {
                truncationState.mark();
                break;
            }
            String key = trimText(String.valueOf(entry.getKey()), limits.maxFieldChars, limits, truncationState);
            Object value = sanitizeValue(entry.getValue(), limits, truncationState, depth - 1, visited);
            sanitized.put(key, value);
            index++;
        }
        visited.remove(map);
        return sanitized;
    }

    private Object sanitizeList(List<?> list,
                                Limits limits,
                                TruncationState truncationState,
                                int depth,
                                IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(list, Boolean.TRUE) != null) {
            truncationState.mark();
            return List.of("<circular>");
        }
        List<Object> sanitized = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            if (limits.maxListItems > 0 && index >= limits.maxListItems) {
                truncationState.mark();
                break;
            }
            sanitized.add(sanitizeValue(item, limits, truncationState, depth - 1, visited));
            index++;
        }
        visited.remove(list);
        return sanitized;
    }

    private Object sanitizeArray(Object array,
                                 Limits limits,
                                 TruncationState truncationState,
                                 int depth,
                                 IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(array, Boolean.TRUE) != null) {
            truncationState.mark();
            return List.of("<circular>");
        }
        List<Object> sanitized = new ArrayList<>();
        int length = java.lang.reflect.Array.getLength(array);
        for (int index = 0; index < length; index++) {
            if (limits.maxListItems > 0 && index >= limits.maxListItems) {
                truncationState.mark();
                break;
            }
            Object item = java.lang.reflect.Array.get(array, index);
            sanitized.add(sanitizeValue(item, limits, truncationState, depth - 1, visited));
        }
        visited.remove(array);
        return sanitized;
    }

    private String trimText(String value,
                            int maxFieldChars,
                            Limits limits,
                            TruncationState truncationState) {
        if (value == null) {
            return null;
        }
        String text = value;
        if (maxFieldChars > 0 && text.length() > maxFieldChars) {
            text = text.substring(0, maxFieldChars);
            truncationState.mark();
        }
        if (limits.maxChars > 0 && truncationState.totalChars >= limits.maxChars) {
            truncationState.mark();
            return "";
        }
        if (limits.maxChars > 0 && truncationState.totalChars + text.length() > limits.maxChars) {
            int remain = Math.max(0, limits.maxChars - truncationState.totalChars);
            text = text.substring(0, remain);
            truncationState.mark();
        }
        truncationState.totalChars += text.length();
        return text;
    }

    private void mergeRefs(Map<String, String> target, Object refsObj) {
        if (!(refsObj instanceof Map<?, ?> map)) {
            return;
        }
        map.forEach((key, value) -> {
            if (key != null && value != null) {
                target.put(String.valueOf(key), String.valueOf(value));
            }
        });
    }

    private void mergeRefValue(Map<String, String> target, String key, Object value) {
        String text = readString(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private String readString(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    /**
     * 原始结果裁剪参数。
     */
    private static final class Limits {
        private final int maxChars;
        private final int maxFieldChars;
        private final int maxListItems;

        private Limits(int maxChars, int maxFieldChars, int maxListItems) {
            this.maxChars = Math.max(0, maxChars);
            this.maxFieldChars = Math.max(0, maxFieldChars);
            this.maxListItems = Math.max(0, maxListItems);
        }
    }

    /**
     * 截断状态。
     */
    private static final class TruncationState {
        private boolean truncated;
        private int totalChars;

        private void mark() {
            this.truncated = true;
        }
    }
}
