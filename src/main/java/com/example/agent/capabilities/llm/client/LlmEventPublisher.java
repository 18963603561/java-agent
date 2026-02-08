package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.client.events.LlmEventPayloadMapper;
import com.example.agent.capabilities.llm.client.events.LlmParseEventPayload;
import com.example.agent.capabilities.llm.client.events.LlmPromptEventPayload;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * LLM 事件发布器。
 *
 * <p>用途：统一封装提示词事件、解析事件与追踪事件发布逻辑，避免调用服务承担事件细节。
 * <p>输入：调用上下文、执行结果与元数据。
 * <p>输出：发布到系统事件总线的 {@link StreamEvent}。
 */
@Component
public class LlmEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LlmEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final LlmEventPayloadMapper eventPayloadMapper;

    public LlmEventPublisher(ApplicationEventPublisher eventPublisher,
                             EventStreamService eventStreamService,
                             LlmEventPayloadMapper eventPayloadMapper) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.eventPayloadMapper = eventPayloadMapper;
    }

    /**
     * 发布提示词事件。
     *
     * @param publishEnabled 是否启用事件发布
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 外部序号计数器
     * @param phase 阶段
     * @param modelId 模型标识
     * @param request 模型请求
     * @param metadata 元数据
     */
    public void publishPromptEvent(boolean publishEnabled,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String phase,
                                   String modelId,
                                   ModelRequest request,
                                   Map<String, Object> metadata) {
        if (!publishEnabled) {
            if (tenantContext != null) {
                log.info("LLM 提示词事件发布已关闭, tenantId={}, workflowId={}, phase={}, modelId={}",
                        tenantContext.getTenantId(), workflowId, phase, modelId);
            }
            return;
        }
        if (tenantContext == null || !hasWorkflowId(workflowId)) {
            log.debug("跳过 LLM 提示词事件发布, tenantContext/workflowId 缺失, workflowId={}", workflowId);
            return;
        }

        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        List<String> messageRoles = resolveMessageRoles(request);
        Integer messageCount = messageRoles.isEmpty() ? null : messageRoles.size();
        String provider = metadata != null && metadata.get("provider") != null
                ? String.valueOf(metadata.get("provider"))
                : null;
        LlmPromptEventPayload payload = new LlmPromptEventPayload(
                phase,
                request != null && request.getScene() != null ? request.getScene().name() : null,
                provider,
                modelId,
                workflowId,
                tenantContext.getTraceId(),
                LlmResultStatus.SUCCESS.toPayloadValue(),
                null,
                request != null ? request.getPrompt() : null,
                messageCount,
                messageRoles,
                trace,
                metadata);
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PROMPT, eventPayloadMapper.toMap(payload));
    }

    /**
     * 发布解析事件。
     *
     * @param publishEnabled 是否启用事件发布
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 外部序号计数器
     * @param phase 阶段
     * @param executionResult 执行结果
     * @param metadata 元数据
     */
    public void publishParseEvent(boolean publishEnabled,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  String phase,
                                  LlmExecutionResult executionResult,
                                  Map<String, Object> metadata) {
        if (!publishEnabled || tenantContext == null || !hasWorkflowId(workflowId) || executionResult == null) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        LlmExecutionUsage usage = executionResult.getUsage();
        LlmExecutionFailure failure = executionResult.getFailure();
        LlmParseEventPayload payload = new LlmParseEventPayload(
                phase,
                executionResult.getScene(),
                executionResult.getProvider(),
                executionResult.getModelId(),
                workflowId,
                executionResult.getTraceId(),
                executionResult.getStatus() != null ? executionResult.getStatus().toPayloadValue() : null,
                executionResult.getRawRef(),
                executionResult.getRawText(),
                usage != null ? usage.getInputTokens() : null,
                usage != null ? usage.getOutputTokens() : null,
                usage != null ? usage.getTotalTokens() : null,
                failure != null ? failure.getErrorCode() : null,
                failure != null ? failure.getErrorMessage() : null,
                failure != null ? failure.getExceptionType() : null,
                failure != null ? failure.isRetriable() : null,
                trace,
                metadata);
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PARSE, eventPayloadMapper.toMap(payload));
    }

    /**
     * 发布提示词追踪事件。
     *
     * @param publishEnabled 是否启用事件发布
     * @param trace 追踪信息
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 外部序号计数器
     * @param phase 阶段
     * @param modelId 模型标识
     */
    public void publishPromptTraceEvent(boolean publishEnabled,
                                        PromptTrace trace,
                                        TenantContext tenantContext,
                                        String workflowId,
                                        AtomicLong seqCounter,
                                        String phase,
                                        String modelId) {
        if (!publishEnabled || trace == null || tenantContext == null || !hasWorkflowId(workflowId)) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        LlmParseEventPayload payload = new LlmParseEventPayload(
                phase,
                null,
                null,
                modelId,
                workflowId,
                tenantContext.getTraceId(),
                LlmResultStatus.SUCCESS.toPayloadValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                trace,
                Map.of());
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PARSE, eventPayloadMapper.toMap(payload));
    }

    private boolean hasWorkflowId(String workflowId) {
        return workflowId != null && !workflowId.isBlank();
    }

    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    private List<String> resolveMessageRoles(ModelRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return List.of();
        }
        return request.getMessages().stream()
                .map(message -> message != null && message.getRole() != null ? message.getRole().name() : "USER")
                .toList();
    }

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              long seq,
                              EventType type,
                              Map<String, Object> payload) {
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }
}

