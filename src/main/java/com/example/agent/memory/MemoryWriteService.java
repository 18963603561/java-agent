package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.runtime.RuntimeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆写入服务，负责将任务输入与结果落盘为会话记忆。
 */
@Service
public class MemoryWriteService {

    private static final Logger log = LoggerFactory.getLogger(MemoryWriteService.class);

    private static final String CONTEXT_WRITE_ENABLED = "memoryWriteEnabled";

    /**
     * 记忆存取服务。
     */
    private final MemoryStore memoryStore;

    /**
     * 记忆写入配置。
     */
    private final MemoryWriteProperties properties;

    /**
     * JSON 序列化工具。
     */
    private final ObjectMapper objectMapper;

    public MemoryWriteService(MemoryStore memoryStore,
                              MemoryWriteProperties properties,
                              ObjectMapper objectMapper) {
        this.memoryStore = memoryStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存任务相关记忆，不影响主流程。
     *
     * @param request 任务请求
     * @param result 运行结果
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     */
    public void saveTaskMemory(TaskRequest request,
                               RuntimeResult result,
                               TenantContext tenantContext,
                               String taskId) {
        if (tenantContext == null) {
            return;
        }
        if (!resolveEnabled(request)) {
            log.debug("记忆写入关闭, tenantId={}", tenantContext.getTenantId());
            return;
        }
        if (request == null) {
            return;
        }
        String sessionId = request.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            log.debug("记忆写入跳过, tenantId={}, reason=session_missing", tenantContext.getTenantId());
            return;
        }

        int saved = 0;
        log.info("记忆写入开始, tenantId={}, sessionId={}, taskId={}",
                tenantContext.getTenantId(), sessionId, taskId);

        if (properties.isSaveUserQuery() && StringUtils.hasText(request.getQuery())) {
            MemoryRecord record = new MemoryRecord();
            record.setSessionId(sessionId);
            record.setTaskId(taskId);
            record.setContent(trimText(request.getQuery(), properties.getMaxRecordChars()));
            record.setSummary(trimText(request.getQuery(), properties.getMaxSummaryChars()));
            record.setLayer("recent");
            if (saveSafely(record, tenantContext)) {
                saved++;
            }
        }

        if (properties.isSaveFinalOutput() && result != null && result.getFinalOutput() != null) {
            String outputText = serializeOutput(result.getFinalOutput());
            String summary = buildOutputSummary(result.getPlanSummary(), outputText);
            MemoryRecord record = new MemoryRecord();
            record.setSessionId(sessionId);
            record.setTaskId(taskId);
            record.setContent(trimText(outputText, properties.getMaxRecordChars()));
            record.setSummary(trimText(summary, properties.getMaxSummaryChars()));
            record.setLayer("recent");
            if (saveSafely(record, tenantContext)) {
                saved++;
            }
        }

        log.info("记忆写入完成, tenantId={}, sessionId={}, taskId={}, count={}",
                tenantContext.getTenantId(), sessionId, taskId, saved);
    }

    private boolean resolveEnabled(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return properties.isEnabled();
        }
        Map<String, Object> context = request.getContext();
        Object value = context.get(CONTEXT_WRITE_ENABLED);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return properties.isEnabled();
    }

    private boolean saveSafely(MemoryRecord record, TenantContext tenantContext) {
        try {
            memoryStore.save(record, tenantContext);
            return true;
        } catch (Exception ex) {
            log.error("记忆写入失败, tenantId={}, sessionId={}, taskId={}",
                    tenantContext.getTenantId(), record.getSessionId(), record.getTaskId(), ex);
            return false;
        }
    }

    private String serializeOutput(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception ex) {
            return String.valueOf(output);
        }
    }

    private String buildOutputSummary(String planSummary, String outputText) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(planSummary)) {
            builder.append("planSummary: ").append(planSummary.trim());
        }
        if (StringUtils.hasText(outputText)) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("finalOutput: ").append(trimText(outputText, 200));
        }
        String summary = builder.toString().trim();
        return summary.isEmpty() ? null : summary;
    }

    private String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }
}
