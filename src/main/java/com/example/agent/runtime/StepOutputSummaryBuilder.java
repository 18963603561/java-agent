package com.example.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤输出摘要构建器，用于生成摘要层与指纹层数据。
 */
@Component
public class StepOutputSummaryBuilder {

    private static final Logger log = LoggerFactory.getLogger(StepOutputSummaryBuilder.class);

    private final StepSummaryProperties properties;

    public StepOutputSummaryBuilder(StepSummaryProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断是否启用摘要生成。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties != null && properties.isEnable();
    }

    /**
     * 生成摘要字段，返回包含 outputSummary/toolResultSummary/stepSummary/outputDigest/truncated 的结构。
     *
     * @param stepId 步骤标识
     * @param stepType 步骤类型
     * @param status 步骤状态
     * @param output 输出内容
     * @param toolName 工具名称（可选）
     * @param error 异常信息（可选）
     * @param attempt 尝试次数（可选）
     * @return 摘要结构
     */
    public Map<String, Object> build(String stepId,
                                     String stepType,
                                     String status,
                                     Object output,
                                     String toolName,
                                     Object error,
                                     Integer attempt) {
        if (!isEnabled()) {
            return Collections.emptyMap();
        }

        SummaryLimits limits = SummaryLimits.from(properties);
        TruncationState truncation = new TruncationState();
        OutputSnapshot snapshot = buildSnapshot(output, limits, truncation);
        String resolvedToolName = resolveToolName(toolName, output);

        Map<String, Object> outputSummary = new LinkedHashMap<>();
        putIfNotNull(outputSummary, "status", status);
        if (output == null) {
            outputSummary.put("hasOutput", false);
            putIfNotNull(outputSummary, "stepId", stepId);
            putIfNotNull(outputSummary, "type", stepType);
            outputSummary.put("summary", "no output");
        }
        if (attempt != null) {
            outputSummary.put("attempt", attempt);
        }
        if (!snapshot.keys.isEmpty()) {
            outputSummary.put("keyFields", snapshot.keys);
        }
        if (StringUtils.hasText(snapshot.sample)) {
            outputSummary.put("sample", snapshot.sample);
        }
        String errorText = resolveErrorText(error, limits, truncation);
        if (StringUtils.hasText(errorText)) {
            outputSummary.put("error", errorText);
        }

        Map<String, Object> toolResultSummary = new LinkedHashMap<>();
        if (StringUtils.hasText(resolvedToolName)) {
            toolResultSummary.put("tool", resolvedToolName);
        }
        if (!snapshot.keys.isEmpty()) {
            toolResultSummary.put("resultKeys", snapshot.keys);
        }
        if (StringUtils.hasText(snapshot.sample)) {
            toolResultSummary.put("sample", snapshot.sample);
        }

        Map<String, Object> stepSummary = new LinkedHashMap<>();
        putIfNotNull(stepSummary, "stepId", stepId);
        putIfNotNull(stepSummary, "type", stepType);
        putIfNotNull(stepSummary, "status", status);
        if (attempt != null) {
            stepSummary.put("attempt", attempt);
        }
        if (StringUtils.hasText(resolvedToolName)) {
            stepSummary.put("tool", resolvedToolName);
        }
        String summaryText = buildStepSummaryText(stepType, status, resolvedToolName, snapshot, limits, truncation);
        if (StringUtils.hasText(summaryText)) {
            stepSummary.put("summary", summaryText);
        }

        Map<String, Object> outputDigest = new LinkedHashMap<>();
        outputDigest.put("keyCount", snapshot.keyCount);
        outputDigest.put("keys", snapshot.keys);
        outputDigest.put("charCount", snapshot.charCount);
        outputDigest.put("truncated", truncation.truncated);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputSummary", outputSummary);
        result.put("toolResultSummary", toolResultSummary);
        result.put("stepSummary", stepSummary);
        result.put("outputDigest", outputDigest);
        result.put("truncated", truncation.truncated);
        return result;
    }

    private OutputSnapshot buildSnapshot(Object output, SummaryLimits limits, TruncationState truncation) {
        KeySnapshot keySnapshot = resolveKeys(output, limits, truncation);
        BoundedSnapshot snapshot = buildBoundedSnapshot(output, limits, truncation);
        String sample = StringUtils.hasText(snapshot.text)
                ? trimText(snapshot.text, limits.maxFieldChars, truncation)
                : null;
        return new OutputSnapshot(keySnapshot.keyCount, keySnapshot.keys, snapshot.charCount, sample);
    }

    private KeySnapshot resolveKeys(Object output, SummaryLimits limits, TruncationState truncation) {
        if (!(output instanceof Map<?, ?> map) || map.isEmpty()) {
            return new KeySnapshot(0, Collections.emptyList());
        }
        List<String> keys = new ArrayList<>();
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Object key : map.keySet()) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            String keyText = key == null ? "null" : safeToString(key);
            keys.add(trimText(keyText, limits.maxFieldChars, truncation));
            index++;
        }
        return new KeySnapshot(map.size(), keys);
    }

    private String resolveToolName(String toolName, Object output) {
        if (StringUtils.hasText(toolName)) {
            return toolName;
        }
        if (output instanceof Map<?, ?> map) {
            Object value = map.get("tool");
            if (value == null) {
                value = map.get("toolName");
            }
            if (value != null) {
                return safeToString(value);
            }
        }
        return null;
    }

    private String resolveErrorText(Object error, SummaryLimits limits, TruncationState truncation) {
        if (error == null) {
            return null;
        }
        String text;
        if (error instanceof Throwable throwable) {
            String message = throwable.getMessage();
            text = message == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getClass().getSimpleName() + ": " + message;
        } else {
            text = safeToString(error);
        }
        return trimText(text, limits.maxFieldChars, truncation);
    }

    private String buildStepSummaryText(String stepType,
                                        String status,
                                        String toolName,
                                        OutputSnapshot snapshot,
                                        SummaryLimits limits,
                                        TruncationState truncation) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(stepType)) {
            parts.add("type=" + stepType);
        }
        if (StringUtils.hasText(status)) {
            parts.add("status=" + status);
        }
        if (StringUtils.hasText(toolName)) {
            parts.add("tool=" + toolName);
        }
        if (snapshot.keyCount > 0) {
            parts.add(String.format(Locale.ROOT, "keyCount=%d", snapshot.keyCount));
        }
        if (snapshot.charCount > 0) {
            parts.add(String.format(Locale.ROOT, "charCount=%d", snapshot.charCount));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return trimText(String.join(", ", parts), limits.maxFieldChars, truncation);
    }

    private BoundedSnapshot buildBoundedSnapshot(Object output, SummaryLimits limits, TruncationState truncation) {
        if (output == null) {
            return new BoundedSnapshot("", 0);
        }
        SnapshotWriter writer = new SnapshotWriter(limits.maxChars, truncation);
        java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
        appendValue(output, writer, limits, truncation, visited);
        return new BoundedSnapshot(writer.toString(), writer.length());
    }

    private void appendValue(Object value,
                             SnapshotWriter writer,
                             SummaryLimits limits,
                             TruncationState truncation,
                             java.util.IdentityHashMap<Object, Boolean> visited) {
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
            appendMap(map, writer, limits, truncation, visited);
            return;
        }
        if (value instanceof List<?> list) {
            appendList(list, writer, limits, truncation, visited);
            return;
        }
        if (value.getClass().isArray()) {
            appendArray(value, writer, limits, truncation, visited);
            return;
        }
        writer.append(trimText(safeToString(value), limits.maxFieldChars, truncation));
    }

    private void appendMap(Map<?, ?> map,
                           SnapshotWriter writer,
                           SummaryLimits limits,
                           TruncationState truncation,
                           java.util.IdentityHashMap<Object, Boolean> visited) {
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
            writer.append(trimText(keyText, limits.maxFieldChars, truncation));
            writer.append(":");
            appendValue(entry.getValue(), writer, limits, truncation, visited);
            index++;
        }
        writer.append("}");
        visited.remove(map);
    }

    private void appendList(List<?> list,
                            SnapshotWriter writer,
                            SummaryLimits limits,
                            TruncationState truncation,
                            java.util.IdentityHashMap<Object, Boolean> visited) {
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
            appendValue(item, writer, limits, truncation, visited);
            index++;
        }
        writer.append("]");
        visited.remove(list);
    }

    private void appendArray(Object array,
                             SnapshotWriter writer,
                             SummaryLimits limits,
                             TruncationState truncation,
                             java.util.IdentityHashMap<Object, Boolean> visited) {
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
            appendValue(item, writer, limits, truncation, visited);
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

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value);
        // 异常捕获：记录上下文并按当前策略处理
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

    private static final class SummaryLimits {
        private final int maxChars;
        private final int maxListItems;
        private final int maxFieldChars;

        private SummaryLimits(int maxChars, int maxListItems, int maxFieldChars) {
            this.maxChars = maxChars;
            this.maxListItems = maxListItems;
            this.maxFieldChars = maxFieldChars;
        }

        private static SummaryLimits from(StepSummaryProperties properties) {
            if (properties == null) {
                return new SummaryLimits(0, 0, 0);
            }
            return new SummaryLimits(properties.getMaxChars(),
                    properties.getMaxListItems(),
                    properties.getMaxFieldChars());
        }
    }

    private static final class OutputSnapshot {
        private final int keyCount;
        private final List<String> keys;
        private final int charCount;
        private final String sample;

        private OutputSnapshot(int keyCount, List<String> keys, int charCount, String sample) {
            this.keyCount = keyCount;
            this.keys = keys;
            this.charCount = charCount;
            this.sample = sample;
        }
    }

    private static final class KeySnapshot {
        private final int keyCount;
        private final List<String> keys;

        private KeySnapshot(int keyCount, List<String> keys) {
            this.keyCount = keyCount;
            this.keys = keys;
        }
    }

    private static final class TruncationState {
        private boolean truncated;

        private void markTruncated() {
            this.truncated = true;
        }
    }

    private static final class BoundedSnapshot {
        private final String text;
        private final int charCount;

        private BoundedSnapshot(String text, int charCount) {
            this.text = text;
            this.charCount = charCount;
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

        private int length() {
            return builder.length();
        }

        /**
         * 获取当前缓冲区文本内容。
         *
         * @return 当前文本
         */
        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
