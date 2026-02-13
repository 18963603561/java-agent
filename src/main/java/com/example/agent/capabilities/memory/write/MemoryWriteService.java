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
        // 判断租户上下文是否为空，空时直接返回。
        if (tenantContext == null) {
            // 返回空值，避免空指针。
            return;
        }
        // 调用上下文解析器获取写入上下文。
        MemoryWriteContext writeContext = contextResolver.resolve(request);
        // 读取租户标识。
        String tenantId = tenantContext.getTenantId();
        // 判断写入是否启用，未启用时跳过。
        if (!writeContext.isEnabled()) {
            // 记录写入关闭日志。
            log.debug("记忆写入关闭, tenantId={}", tenantId);
            // 返回空值，结束写入流程。
            return;
        }
        // 判断请求是否为空，空时直接返回。
        if (request == null) {
            // 返回空值，避免后续处理空指针。
            return;
        }
        // 判断会话是否有效，缺失时跳过写入。
        if (!writeContext.hasValidSession()) {
            // 记录会话缺失日志。
            log.debug("记忆写入跳过, tenantId={}, reason=session_missing", tenantId);
            // 返回空值，结束写入流程。
            return;
        }
        // 读取会话标识。
        String sessionId = writeContext.getSessionId();
        // 读取工作流标识。
        String workflowId = writeContext.getWorkflowId();

        // 初始化写入统计对象。
        MemoryWriteStats stats = new MemoryWriteStats();
        // 记录写入开始日志。
        log.info("记忆写入开始, tenantId={}, workflowId={}, sessionId={}, taskId={}",
                tenantId, workflowId, sessionId, taskId);

        // 判断是否保存用户查询且查询文本非空。
        if (properties.isSaveUserQuery() && StringUtils.hasText(request.getQuery())) {
            // 调用脱敏处理器处理用户查询文本。
            RedactionResult redaction = redactionProcessor.redactForWrite(request.getQuery(), "userQuery");
            // 判断脱敏结果是否拒写，拒写时记录统计。
            if (redaction.isRejected()) {
                // 记录拒写次数。
                stats.incrementRejected();
            } else {
                // 记录脱敏计数。
                stats.addRedactions(redaction.getRedactedCount());
                // 构建最近层记忆记录。
                MemoryRecord record = recordFactory.buildRecentRecord(
                        sessionId,
                        taskId,
                        redaction.getRedactedText(),
                        redaction.getRedactedText());
                // 调用持久化网关保存记录。
                if (persistenceGateway.saveSafely(record, tenantContext)) {
                    // 记录保存成功次数。
                    stats.incrementSaved();
                }
            }
        }

        // 判断是否保存最终输出且最终输出存在。
        if (properties.isSaveFinalOutput() && result != null && result.getFinalOutput() != null) {
            // 调用序列化器序列化最终输出。
            String outputText = serializer.serializeOutput(result.getFinalOutput(), tenantContext, request, taskId);
            // 调用序列化器构建输出摘要。
            String summary = serializer.buildOutputSummary(result.getPlanSummary(), result.getFinalOutput(), outputText);
            // 调用脱敏处理器处理最终输出文本。
            RedactionResult outputRedaction = redactionProcessor.redactForWrite(outputText, "finalOutput");
            // 调用脱敏处理器处理最终输出摘要。
            RedactionResult summaryRedaction = redactionProcessor.redactForWrite(summary, "finalOutputSummary");
            // 判断脱敏结果是否拒写，拒写时记录统计。
            if (outputRedaction.isRejected() || summaryRedaction.isRejected()) {
                // 记录拒写次数。
                stats.incrementRejected();
            } else {
                // 记录输出脱敏计数。
                stats.addRedactions(outputRedaction.getRedactedCount());
                // 记录摘要脱敏计数。
                stats.addRedactions(summaryRedaction.getRedactedCount());
                // 构建最近层记忆记录。
                MemoryRecord record = recordFactory.buildRecentRecord(
                        sessionId,
                        taskId,
                        outputRedaction.getRedactedText(),
                        summaryRedaction.getRedactedText());
                // 调用持久化网关保存记录。
                if (persistenceGateway.saveSafely(record, tenantContext)) {
                    // 记录保存成功次数。
                    stats.incrementSaved();
                }
            }
        }

        // 记录写入完成日志。
        log.info("记忆写入完成, tenantId={}, workflowId={}, sessionId={}, taskId={}, count={}, writesRejectedCount={}, "
                        + "redactionsAppliedCount={}, enabledFlags={}, rejectOnSecrets={}, redactOnPii={}",
                tenantId, workflowId, sessionId, taskId,
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
