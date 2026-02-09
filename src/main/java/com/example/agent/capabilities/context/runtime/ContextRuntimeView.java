package com.example.agent.capabilities.context.runtime;

import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

/**
 * 上下文运行时只读视图。
 *
 * <p>用途：为上下文消费方提供统一、类型安全的读取能力。
 * <p>输入：运行时上下文映射。
 * <p>输出：类型化字段值。
 */
public class ContextRuntimeView {

    /**
     * 日志记录器。
     */
    protected final Logger log;

    /**
     * 指标发布器。
     */
    protected final MetricsPublisher metricsPublisher;

    /**
     * 读取诊断上下文。
     */
    protected final ContextRuntimeReadContext readContext;

    /**
     * 运行时上下文映射。
     */
    protected final Map<String, Object> values;

    /**
     * 构造只读视图。
     *
     * @param values 运行时上下文
     * @param log 日志记录器
     * @param metricsPublisher 指标发布器
     */
    public ContextRuntimeView(Map<String, Object> values,
                              Logger log,
                              MetricsPublisher metricsPublisher) {
        this(values, log, metricsPublisher, null);
    }

    /**
     * 构造只读视图（带读取诊断上下文）。
     *
     * @param values 运行时上下文
     * @param log 日志记录器
     * @param metricsPublisher 指标发布器
     * @param readContext 读取诊断上下文
     */
    public ContextRuntimeView(Map<String, Object> values,
                              Logger log,
                              MetricsPublisher metricsPublisher,
                              ContextRuntimeReadContext readContext) {
        this.values = values;
        this.log = log;
        this.metricsPublisher = metricsPublisher;
        this.readContext = readContext;
    }

    /**
     * 读取字符串字段。
     */
    public String getString(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    /**
     * 读取整数字段。
     */
    public Integer getInteger(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                recordTypeMismatch(key, "Integer", value, ex);
                return null;
            }
        }
        recordTypeMismatch(key, "Integer", value, null);
        return null;
    }

    /**
     * 读取布尔字段。
     */
    public Boolean getBoolean(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            String normalized = text.trim();
            if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
                return Boolean.parseBoolean(normalized);
            }
        }
        recordTypeMismatch(key, "Boolean", value, null);
        return null;
    }

    /**
     * 读取字符串列表字段。
     */
    public List<String> getStringList(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    result.add(item.toString().trim());
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        recordTypeMismatch(key, "List<String>", value, null);
        return null;
    }

    /**
     * 读取任意列表字段。
     */
    public List<?> getList(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list;
        }
        recordTypeMismatch(key, "List", value, null);
        return null;
    }

    /**
     * 读取对象映射字段。
     */
    public Map<?, ?> getMap(String key) {
        Object value = getRaw(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        recordTypeMismatch(key, "Map", value, null);
        return null;
    }

    /**
     * 读取证据包。
     */
    public EvidencePack getEvidencePack() {
        Object value = getRaw(ContextRuntimeKeys.EVIDENCE_PACK);
        if (value == null) {
            return null;
        }
        if (value instanceof EvidencePack evidencePack) {
            return evidencePack;
        }
        recordTypeMismatch(ContextRuntimeKeys.EVIDENCE_PACK, "EvidencePack", value, null);
        return null;
    }

    /**
     * 读取研究引用列表。
     */
    public List<?> getCitationItems() {
        List<?> citations = getList(ContextRuntimeKeys.CITATIONS);
        if (citations != null) {
            return citations;
        }
        return getList(ContextRuntimeKeys.RESEARCH_CITATIONS);
    }

    /**
     * 读取长期记忆引用列表。
     */
    public List<?> getLongTermMemoryRefs() {
        return getList(ContextRuntimeKeys.LONG_TERM_MEMORY_REFS);
    }

    /**
     * 读取原始值。
     */
    protected Object getRaw(String key) {
        if (values == null || !StringUtils.hasText(key)) {
            return null;
        }
        return values.get(key);
    }

    /**
     * 记录类型不匹配告警与指标。
     */
    protected void recordTypeMismatch(String key, String expectedType, Object actualValue, Exception ex) {
        String actualType = actualValue == null ? "null" : actualValue.getClass().getSimpleName();
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags(
                    "context_runtime_read_failed_total",
                    "key", sanitizeTag(key),
                    "type", sanitizeTag(expectedType));
        }
        if (log != null) {
            String tenantId = readContext != null ? readContext.getTenantId() : null;
            String workflowId = readContext != null ? readContext.getWorkflowId() : null;
            String taskId = readContext != null ? readContext.getTaskId() : null;
            String stage = readContext != null ? readContext.getStage() : null;
            if (ex != null) {
                log.warn("上下文读取类型不匹配, tenantId={}, workflowId={}, taskId={}, stage={}, key={}, expectedType={}, actualType={}",
                        tenantId,
                        workflowId,
                        taskId,
                        stage,
                        key,
                        expectedType,
                        actualType,
                        ex);
            } else {
                log.warn("上下文读取类型不匹配, tenantId={}, workflowId={}, taskId={}, stage={}, key={}, expectedType={}, actualType={}",
                        tenantId,
                        workflowId,
                        taskId,
                        stage,
                        key,
                        expectedType,
                        actualType);
            }
        }
    }

    /**
     * 清洗指标标签值。
     */
    protected String sanitizeTag(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }
}
