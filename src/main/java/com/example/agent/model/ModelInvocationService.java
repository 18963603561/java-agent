package com.example.agent.model;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.streaming.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 模型调用协调器，负责统一调用模型并发布事件。
 */
@Service
public class ModelInvocationService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(ModelInvocationService.class);

    /**
     * 模型调用客户端。
     */
    private final LlmClient llmClient;
    /**
     * 模型路由器。
     */
    private final ModelRouter modelRouter;
    /**
     * 事件发布器。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 事件流服务。
     */
    private final EventStreamService eventStreamService;

    public ModelInvocationService(LlmClient llmClient,
                                  ModelRouter modelRouter,
                                  ApplicationEventPublisher eventPublisher,
                                  EventStreamService eventStreamService) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
    }

    /**
     * 调用模型并发布事件。
     *
     * @param request 模型请求
     * @param scene 模型场景
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param phase 调用阶段
     * @param metadata 额外上下文
     * @return 模型响应
     */
    public ModelResponse invoke(ModelRequest request,
                                ModelScene scene,
                                TenantContext tenantContext,
                                String workflowId,
                                AtomicLong seqCounter,
                                String phase,
                                Map<String, Object> metadata) {
        ModelRequest safeRequest = request != null ? request : new ModelRequest();
        safeRequest.setScene(scene);
        ModelDefinition definition = modelRouter.route(scene);
        String modelId = definition != null ? definition.getModelId() : null;

        publishPromptEvent(tenantContext, workflowId, seqCounter, phase, modelId, safeRequest, metadata);

        long startNs = System.nanoTime();
        try {
            log.info("模型调用开始, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    modelId,
                    phase);
            ModelResponse response = llmClient.generate(safeRequest);
            publishOutputEvent(tenantContext, workflowId, seqCounter, phase, response, metadata);
            log.info("模型调用完成, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, latencyMs={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    response != null ? response.getModelId() : modelId,
                    phase,
                    (System.nanoTime() - startNs) / 1_000_000);
            return response;
        } catch (ErrorCodeException ex) {
            log.error("模型调用失败, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, code={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    modelId,
                    phase,
                    ex.getErrorCode(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            log.error("模型调用异常, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    modelId,
                    phase,
                    ex);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "模型不可用");
        }
    }

    /**
     * 发布模型请求事件。
     */
    private void publishPromptEvent(TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    String phase,
                                    String modelId,
                                    ModelRequest request,
                                    Map<String, Object> metadata) {
        if (tenantContext == null || workflowId == null) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("phase", phase);
        payload.put("scene", request != null && request.getScene() != null ? request.getScene().name() : null);
        payload.put("modelId", modelId);
        payload.put("prompt", request != null ? request.getPrompt() : null);
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            payload.put("messageCount", request.getMessages().size());
            payload.put("messageRoles", request.getMessages().stream()
                    .map(message -> message != null && message.getRole() != null ? message.getRole().name() : "USER")
                    .toList());
        }
        if (metadata != null) {
            payload.putAll(metadata);
        }
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PROMPT, payload);
    }

    /**
     * 发布模型响应事件。
     */
    private void publishOutputEvent(TenantContext tenantContext,
                                    String workflowId,
                                    AtomicLong seqCounter,
                                    String phase,
                                    ModelResponse response,
                                    Map<String, Object> metadata) {
        if (tenantContext == null || workflowId == null || response == null) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("phase", phase);
        payload.put("modelId", response.getModelId());
        payload.put("content", response.getContent());
        payload.put("inputTokens", response.getInputTokens());
        payload.put("outputTokens", response.getOutputTokens());
        if (metadata != null) {
            payload.putAll(metadata);
        }
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_OUTPUT, payload);
    }

    /**
     * 发布事件到事件流。
     */
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

    /**
     * 获取事件序号。
     */
    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }
}
