package com.example.agent.runtime.model.input;

import com.example.agent.runtime.output.OutputKeys;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上一步上下文快照。
 *
 * <p>用途：统一承载运行时高频“上一步”信息，避免散落的字符串键访问。
 * <p>输入：运行时上下文中的上一步字段。
 * <p>输出：可直接写回运行时上下文，也可输出为扩展映射供边界层消费。
 */
public class LastStepInput {

    private String stepId;
    private String stepType;
    private Map<String, Object> summary;
    private Object rawOutput;
    private String rawRef;
    private Map<String, Object> rawRefs;
    private boolean rawTruncated;
    private Integer outputSize;

    /**
     * 从扩展映射读取上一步信息。
     *
     * @param extensions 扩展映射
     * @return 上一步快照
     */
    public static LastStepInput fromMap(Map<String, Object> extensions) {
        LastStepInput snapshot = new LastStepInput();
        if (extensions == null || extensions.isEmpty()) {
            return snapshot;
        }
        snapshot.setStepId(toText(extensions.get("lastStepId")));
        snapshot.setStepType(toText(extensions.get("lastStepType")));
        snapshot.setSummary(toObjectMap(extensions.get("lastStepSummary")));
        snapshot.setRawOutput(extensions.get("lastStepRawOutput"));
        snapshot.setRawRef(toText(extensions.get("lastStepRawRef")));
        snapshot.setRawRefs(toObjectMap(extensions.get("lastStepRawRefs")));
        snapshot.setRawTruncated(toBoolean(extensions.get("lastStepRawTruncated")));
        snapshot.setOutputSize(toInteger(extensions.get("lastOutputSize")));
        return snapshot;
    }

    /**
     * 输出为扩展映射。
     *
     * @return 扩展映射
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfHasText(map, "lastStepId", stepId);
        putIfHasText(map, "lastStepType", stepType);
        if (summary != null && !summary.isEmpty()) {
            map.put("lastStepSummary", new LinkedHashMap<>(summary));
        }
        if (rawOutput != null) {
            map.put("lastStepRawOutput", rawOutput);
        }
        putIfHasText(map, "lastStepRawRef", rawRef);
        if (rawRefs != null && !rawRefs.isEmpty()) {
            map.put("lastStepRawRefs", new LinkedHashMap<>(rawRefs));
        }
        map.put("lastStepRawTruncated", rawTruncated);
        if (outputSize != null) {
            map.put("lastOutputSize", outputSize);
        }
        return map;
    }

    /**
     * 输出为步骤摘要条目。
     *
     * @return 摘要条目
     */
    public Map<String, Object> toStepHistoryItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        putIfHasText(item, "stepId", stepId);
        putIfHasText(item, "type", stepType);
        if (rawRef != null && !rawRef.isBlank()) {
            item.put(OutputKeys.RAW_REF, rawRef);
        }
        if (summary != null && !summary.isEmpty()) {
            item.put(OutputKeys.STEP_SUMMARY, new LinkedHashMap<>(summary));
        }
        return item;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public Object getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(Object rawOutput) {
        this.rawOutput = rawOutput;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }

    public Map<String, Object> getRawRefs() {
        return rawRefs;
    }

    public void setRawRefs(Map<String, Object> rawRefs) {
        this.rawRefs = rawRefs;
    }

    public boolean isRawTruncated() {
        return rawTruncated;
    }

    public void setRawTruncated(boolean rawTruncated) {
        this.rawTruncated = rawTruncated;
    }

    public Integer getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(Integer outputSize) {
        this.outputSize = outputSize;
    }

    private static void putIfHasText(Map<String, Object> target, String key, String value) {
        if (target == null || key == null || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private static Map<String, Object> toObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, val) -> result.put(String.valueOf(key), val));
        return result;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}

