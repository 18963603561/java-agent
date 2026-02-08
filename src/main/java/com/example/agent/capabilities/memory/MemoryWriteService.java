package com.example.agent.capabilities.memory;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;
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

    /**
     * 脱敏服务。
     */
    private final RedactionService redactionService;

    public MemoryWriteService(MemoryStore memoryStore,
                              MemoryWriteProperties properties,
                              ObjectMapper objectMapper,
                              RedactionService redactionService) {
        this.memoryStore = memoryStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redactionService = redactionService;
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
        String workflowId = readString(request.getContext(), "workflowId");
        if (!StringUtils.hasText(sessionId)) {
            log.debug("记忆写入跳过, tenantId={}, reason=session_missing", tenantContext.getTenantId());
            return;
        }

        int saved = 0;
        int writesRejectedCount = 0;
        int redactionsAppliedCount = 0;
        log.info("记忆写入开始, tenantId={}, workflowId={}, sessionId={}, taskId={}",
                tenantContext.getTenantId(), workflowId, sessionId, taskId);

        if (properties.isSaveUserQuery() && StringUtils.hasText(request.getQuery())) {
            RedactionResult redaction = applyRedaction(request.getQuery(), RedactionStage.WRITE, "userQuery");
            if (redaction.isRejected()) {
                writesRejectedCount++;
            } else {
                redactionsAppliedCount += redaction.getRedactedCount();
                String text = redaction.getRedactedText();
                MemoryRecord record = new MemoryRecord();
                record.setSessionId(sessionId);
                record.setTaskId(taskId);
                record.setContent(trimText(text, properties.getMaxRecordChars()));
                record.setSummary(trimText(text, properties.getMaxSummaryChars()));
                record.setLayer(MemoryLayer.RECENT.value());
                if (saveSafely(record, tenantContext)) {
                    saved++;
                }
            }
        }

        if (properties.isSaveFinalOutput() && result != null && result.getFinalOutput() != null) {
            String outputText = serializeOutput(result.getFinalOutput());
            String summary = buildOutputSummary(result.getPlanSummary(), outputText);
            RedactionResult outputRedaction = applyRedaction(outputText, RedactionStage.WRITE, "finalOutput");
            RedactionResult summaryRedaction = applyRedaction(summary, RedactionStage.WRITE, "finalOutputSummary");
            if (outputRedaction.isRejected() || summaryRedaction.isRejected()) {
                writesRejectedCount++;
            } else {
                redactionsAppliedCount += outputRedaction.getRedactedCount();
                redactionsAppliedCount += summaryRedaction.getRedactedCount();
                MemoryRecord record = new MemoryRecord();
                record.setSessionId(sessionId);
                record.setTaskId(taskId);
                record.setContent(trimText(outputRedaction.getRedactedText(), properties.getMaxRecordChars()));
                record.setSummary(trimText(summaryRedaction.getRedactedText(), properties.getMaxSummaryChars()));
                record.setLayer(MemoryLayer.RECENT.value());
                if (saveSafely(record, tenantContext)) {
                    saved++;
                }
            }
        }

        log.info("记忆写入完成, tenantId={}, workflowId={}, sessionId={}, taskId={}, count={}, writesRejectedCount={}, "
                        + "redactionsAppliedCount={}, enabledFlags={}, rejectOnSecrets={}, redactOnPii={}",
                tenantContext.getTenantId(), workflowId, sessionId, taskId, saved, writesRejectedCount,
                redactionsAppliedCount,
                redactionService != null && redactionService.isEnabled(),
                redactionService != null && redactionService.isRejectOnSecrets(),
                redactionService != null && redactionService.isRedactOnPii());
    }

    /**
     * 保存观察记录，用于 ReAct 循环的观察阶段。
     *
     * @param request 任务请求
     * @param observation 观察内容
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     */
    public void saveObservationMemory(TaskRequest request,
                                      String observation,
                                      TenantContext tenantContext,
                                      String taskId) {
        if (tenantContext == null || !properties.isSaveObservation()) {
            return;
        }
        if (!resolveEnabled(request)) {
            return;
        }
        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            return;
        }
        if (!StringUtils.hasText(observation)) {
            return;
        }
        RedactionResult redaction = applyRedaction(observation, RedactionStage.WRITE, "observation");
        if (redaction.isRejected()) {
            String workflowId = readString(request.getContext(), "workflowId");
            log.info("观察记忆拒写, tenantId={}, workflowId={}, sessionId={}, taskId={}",
                    tenantContext.getTenantId(), workflowId, request.getSessionId(), taskId);
            return;
        }
        MemoryRecord record = new MemoryRecord();
        record.setSessionId(request.getSessionId());
        record.setTaskId(taskId);
        record.setContent(trimText(redaction.getRedactedText(), properties.getMaxRecordChars()));
        record.setSummary(trimText(redaction.getRedactedText(), properties.getMaxSummaryChars()));
        record.setLayer(MemoryLayer.RECENT.value());
        saveSafely(record, tenantContext);
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

    private RedactionResult applyRedaction(String text, RedactionStage stage, String fieldKey) {
        if (redactionService == null) {
            RedactionResult result = new RedactionResult();
            result.setRedactedText(text);
            return result;
        }
        return redactionService.apply(text, stage, fieldKey);
    }

    private String readString(Map<String, Object> context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
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
