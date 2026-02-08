package com.example.agent.capabilities.memory.write;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.config.MemoryWriteProperties;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.security.redaction.RedactionResult;
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

    /**
     * 记忆写入配置。
     */
    private final MemoryWriteProperties properties;

    /**
     * 写入上下文解析器。
     */
    private final MemoryWriteContextResolver contextResolver;

    /**
     * 写入脱敏处理器。
     */
    private final MemoryWriteRedactionProcessor redactionProcessor;

    /**
     * 写入序列化处理器。
     */
    private final MemoryWriteSerializer serializer;

    /**
     * 写入记录工厂。
     */
    private final MemoryWriteRecordFactory recordFactory;

    /**
     * 写入持久化网关。
     */
    private final MemoryWritePersistenceGateway persistenceGateway;

    public MemoryWriteService(MemoryWriteProperties properties,
                              MemoryWriteContextResolver contextResolver,
                              MemoryWriteRedactionProcessor redactionProcessor,
                              MemoryWriteSerializer serializer,
                              MemoryWriteRecordFactory recordFactory,
                              MemoryWritePersistenceGateway persistenceGateway) {
        this.properties = properties;
        this.contextResolver = contextResolver;
        this.redactionProcessor = redactionProcessor;
        this.serializer = serializer;
        this.recordFactory = recordFactory;
        this.persistenceGateway = persistenceGateway;
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
        MemoryWriteContext writeContext = contextResolver.resolve(request);
        if (!writeContext.isEnabled()) {
            log.debug("记忆写入关闭, tenantId={}", tenantContext.getTenantId());
            return;
        }
        if (request == null) {
            return;
        }
        if (!writeContext.hasValidSession()) {
            log.debug("记忆写入跳过, tenantId={}, reason=session_missing", tenantContext.getTenantId());
            return;
        }
        String sessionId = writeContext.getSessionId();
        String workflowId = writeContext.getWorkflowId();

        MemoryWriteStats stats = new MemoryWriteStats();
        log.info("记忆写入开始, tenantId={}, workflowId={}, sessionId={}, taskId={}",
                tenantContext.getTenantId(), workflowId, sessionId, taskId);

        if (properties.isSaveUserQuery() && StringUtils.hasText(request.getQuery())) {
            RedactionResult redaction = redactionProcessor.redactForWrite(request.getQuery(), "userQuery");
            if (redaction.isRejected()) {
                stats.incrementRejected();
            } else {
                stats.addRedactions(redaction.getRedactedCount());
                MemoryRecord record = recordFactory.buildRecentRecord(
                        sessionId,
                        taskId,
                        redaction.getRedactedText(),
                        redaction.getRedactedText());
                if (persistenceGateway.saveSafely(record, tenantContext)) {
                    stats.incrementSaved();
                }
            }
        }

        if (properties.isSaveFinalOutput() && result != null && result.getFinalOutput() != null) {
            String outputText = serializer.serializeOutput(result.getFinalOutput(), tenantContext, request, taskId);
            String summary = serializer.buildOutputSummary(result.getPlanSummary(), outputText);
            RedactionResult outputRedaction = redactionProcessor.redactForWrite(outputText, "finalOutput");
            RedactionResult summaryRedaction = redactionProcessor.redactForWrite(summary, "finalOutputSummary");
            if (outputRedaction.isRejected() || summaryRedaction.isRejected()) {
                stats.incrementRejected();
            } else {
                stats.addRedactions(outputRedaction.getRedactedCount());
                stats.addRedactions(summaryRedaction.getRedactedCount());
                MemoryRecord record = recordFactory.buildRecentRecord(
                        sessionId,
                        taskId,
                        outputRedaction.getRedactedText(),
                        summaryRedaction.getRedactedText());
                if (persistenceGateway.saveSafely(record, tenantContext)) {
                    stats.incrementSaved();
                }
            }
        }

        log.info("记忆写入完成, tenantId={}, workflowId={}, sessionId={}, taskId={}, count={}, writesRejectedCount={}, "
                        + "redactionsAppliedCount={}, enabledFlags={}, rejectOnSecrets={}, redactOnPii={}",
                tenantContext.getTenantId(), workflowId, sessionId, taskId,
                stats.getSaved(), stats.getWritesRejectedCount(),
                stats.getRedactionsAppliedCount(),
                redactionProcessor.isEnabled(),
                redactionProcessor.isRejectOnSecrets(),
                redactionProcessor.isRedactOnPii());
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
        MemoryWriteContext writeContext = contextResolver.resolve(request);
        if (!writeContext.isEnabled()) {
            return;
        }
        if (request == null || !writeContext.hasValidSession()) {
            return;
        }
        if (!StringUtils.hasText(observation)) {
            return;
        }
        RedactionResult redaction = redactionProcessor.redactForWrite(observation, "observation");
        if (redaction.isRejected()) {
            log.info("观察记忆拒写, tenantId={}, workflowId={}, sessionId={}, taskId={}",
                    tenantContext.getTenantId(), writeContext.getWorkflowId(), writeContext.getSessionId(), taskId);
            return;
        }
        MemoryRecord record = recordFactory.buildRecentRecord(
                writeContext.getSessionId(),
                taskId,
                redaction.getRedactedText(),
                redaction.getRedactedText());
        persistenceGateway.saveSafely(record, tenantContext);
    }
}
