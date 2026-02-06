package com.example.agent.runtime.step;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时上下文对象。
 *
 * <p>用途：为运行时链路提供显式的上下文边界，避免业务逻辑在各层直接读写 {@code Map} 并依赖魔法键。
 * <p>输入：运行时上下文扩展映射（可变）。
 * <p>输出：提供关键字段的类型化访问方法，并保留扩展字段用于集成边界的动态信息承载。
 * <p>边界：扩展字段允许写入任意键值对，但核心链路应优先使用本对象的显式方法。</p>
 */
public class RuntimeContext {

    private static final String KEY_LAST_STEP_ID = "lastStepId";
    private static final String KEY_LAST_STEP_TYPE = "lastStepType";
    private static final String KEY_LAST_STEP_SUMMARY = "lastStepSummary";
    private static final String KEY_LAST_STEP_RAW_OUTPUT = "lastStepRawOutput";
    private static final String KEY_LAST_STEP_RAW_REF = "lastStepRawRef";
    private static final String KEY_LAST_STEP_RAW_REFS = "lastStepRawRefs";
    private static final String KEY_LAST_STEP_RAW_TRUNCATED = "lastStepRawTruncated";
    private static final String KEY_LAST_OUTPUT_SIZE = "lastOutputSize";
    private static final String KEY_EVALUATION_APPROVAL_GRANTED = "evaluationApprovalGranted";

    /**
     * 扩展字段映射（可变）。
     */
    private final Map<String, Object> extensions;

    public RuntimeContext() {
        this(new HashMap<>());
    }

    public RuntimeContext(Map<String, Object> extensions) {
        this.extensions = extensions == null ? new HashMap<>() : extensions;
    }

    /**
     * 返回可变的扩展映射。
     *
     * <p>注意：该方法主要用于与外部/动态数据边界交互；核心业务逻辑应尽量使用类型化访问方法。</p>
     */
    public Map<String, Object> asMap() {
        return extensions;
    }

    /**
     * 返回不可变的扩展映射视图（用于只读场景）。
     */
    public Map<String, Object> asReadOnlyMap() {
        return Collections.unmodifiableMap(extensions);
    }

    public String getLastStepId() {
        return toText(extensions.get(KEY_LAST_STEP_ID));
    }

    public void setLastStepId(String lastStepId) {
        putOrRemove(KEY_LAST_STEP_ID, lastStepId);
    }

    public String getLastStepType() {
        return toText(extensions.get(KEY_LAST_STEP_TYPE));
    }

    public void setLastStepType(String lastStepType) {
        putOrRemove(KEY_LAST_STEP_TYPE, lastStepType);
    }

    /**
     * 获取上一条步骤摘要。
     *
     * <p>约定：该摘要应尽量为“摘要视图”，避免写入原始输出。</p>
     */
    public Map<String, Object> getLastStepSummary() {
        return toObjectMap(extensions.get(KEY_LAST_STEP_SUMMARY));
    }

    public void setLastStepSummary(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            extensions.remove(KEY_LAST_STEP_SUMMARY);
            return;
        }
        extensions.put(KEY_LAST_STEP_SUMMARY, new LinkedHashMap<>(summary));
    }

    public Object getLastStepRawOutput() {
        return extensions.get(KEY_LAST_STEP_RAW_OUTPUT);
    }

    public void setLastStepRawOutput(Object value) {
        if (value == null) {
            extensions.remove(KEY_LAST_STEP_RAW_OUTPUT);
            return;
        }
        extensions.put(KEY_LAST_STEP_RAW_OUTPUT, value);
    }

    public String getLastStepRawRef() {
        return toText(extensions.get(KEY_LAST_STEP_RAW_REF));
    }

    public void setLastStepRawRef(String rawRef) {
        putOrRemove(KEY_LAST_STEP_RAW_REF, rawRef);
    }

    public Map<String, Object> getLastStepRawRefs() {
        return toObjectMap(extensions.get(KEY_LAST_STEP_RAW_REFS));
    }

    public void setLastStepRawRefs(Map<String, Object> refs) {
        if (refs == null || refs.isEmpty()) {
            extensions.remove(KEY_LAST_STEP_RAW_REFS);
            return;
        }
        extensions.put(KEY_LAST_STEP_RAW_REFS, new LinkedHashMap<>(refs));
    }

    public boolean isLastStepRawTruncated() {
        return extensions.get(KEY_LAST_STEP_RAW_TRUNCATED) instanceof Boolean value && value;
    }

    public void setLastStepRawTruncated(boolean truncated) {
        extensions.put(KEY_LAST_STEP_RAW_TRUNCATED, truncated);
    }

    public Integer getLastOutputSize() {
        Object value = extensions.get(KEY_LAST_OUTPUT_SIZE);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    public void setLastOutputSize(Integer size) {
        if (size == null) {
            extensions.remove(KEY_LAST_OUTPUT_SIZE);
            return;
        }
        extensions.put(KEY_LAST_OUTPUT_SIZE, size);
    }

    public boolean isEvaluationApprovalGranted() {
        Object resolved = extensions.get(KEY_EVALUATION_APPROVAL_GRANTED);
        if (resolved instanceof Boolean value) {
            return value;
        }
        if (resolved instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    public void setEvaluationApprovalGranted(boolean granted) {
        extensions.put(KEY_EVALUATION_APPROVAL_GRANTED, granted);
    }

    private void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            extensions.remove(key);
            return;
        }
        extensions.put(key, value);
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return text;
    }

    private Map<String, Object> toObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}

