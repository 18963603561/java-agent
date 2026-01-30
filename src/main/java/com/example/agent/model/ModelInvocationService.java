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
 * <p>用途：封装模型调用流程，统一日志与事件输出。
 * <p>输入：模型请求、场景、租户上下文与链路信息。
 * <p>输出：模型响应对象。
 * <p>边界：模型不可用时抛出业务异常并标记错误事件。
 * <p>示例：
 * <pre>{@code
 * ModelResponse response = invoke(request, scene, tenantContext, workflowId, seqCounter, "plan", Map.of());
 * }</pre>
 */
@Service
public class ModelInvocationService {

    /**
     * 日志记录器。
     * <p>示例：记录模型标识、调用阶段与耗时。
     */
    private static final Logger log = LoggerFactory.getLogger(ModelInvocationService.class);

    /**
     * 模型调用客户端。
     * <p>示例：调用 {@code LlmClient.generate} 发起模型请求。
     */
    private final LlmClient llmClient;
    /**
     * 模型路由器。
     * <p>示例：根据场景选择模型定义。
     */
    private final ModelRouter modelRouter;
    /**
     * 事件发布器。
     * <p>示例：发布 {@code LLM_PROMPT} 与 {@code LLM_OUTPUT} 事件。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 事件流服务。
     * <p>示例：生成事件序列号。
     */
    private final EventStreamService eventStreamService;

    /**
     * 构造模型调用协调器。
     *
     * @param llmClient 模型调用客户端
     * @param modelRouter 模型路由器
     * @param eventPublisher 事件发布器
     * @param eventStreamService 事件流服务
     */
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
     * <p>输入：模型请求、场景与链路信息。
     * <p>输出：模型响应对象。
     * <p>边界：调用异常会转换为统一错误码。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invoke(request, ModelScene.PLANNER, ctx, wfId, seq, "plan", Map.of("planId","p1"));
     * }</pre>
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

        // 先发布提示词事件，便于审计与追踪。
        publishPromptEvent(tenantContext, workflowId, seqCounter, phase, modelId, safeRequest, metadata);

        long startNs = System.nanoTime();
        try {
            log.info("模型调用开始, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    modelId,
                    phase);
            // 调用模型客户端获取响应。
            ModelResponse response = llmClient.generate(safeRequest);
            // 发布模型输出事件。
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
            // 业务异常直接透传，保留错误码。
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
            // 未知异常统一转换为模型不可用错误。
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
     *
     * <p>输入：租户上下文、工作流标识与模型请求。
     * <p>输出：无。
     * <p>边界：租户上下文或工作流标识为空时不发布。
     * <p>示例：
     * <pre>{@code
     * publishPromptEvent(ctx, wfId, seq, "plan", modelId, request, metadata);
     * }</pre>
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
        // 将提示词信息发布到事件流。
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_PROMPT, payload);
    }

    /**
     * 发布模型响应事件。
     *
     * <p>输入：租户上下文、工作流标识与模型响应。
     * <p>输出：无。
     * <p>边界：响应为空时不发布。
     * <p>示例：
     * <pre>{@code
     * publishOutputEvent(ctx, wfId, seq, "plan", response, metadata);
     * }</pre>
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
        // 将模型输出发布到事件流。
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_OUTPUT, payload);
    }

    /**
     * 发布事件到事件流。
     *
     * <p>输入：租户上下文、工作流标识、事件类型与载荷。
     * <p>输出：无。
     * <p>示例：
     * <pre>{@code
     * publishEvent(ctx, wfId, seq, EventType.LLM_OUTPUT, payload);
     * }</pre>
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
     *
     * <p>输入：租户上下文与工作流标识。
     * <p>输出：事件序号。
     * <p>边界：传入计数器为空时使用事件流服务。
     * <p>示例：
     * <pre>{@code
     * long seq = nextSeq(ctx, wfId, seqCounter);
     * }</pre>
     */
    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            // 优先使用外部传入的序列计数器。
            return seqCounter.incrementAndGet();
        }
        // 序列计数器不存在时从事件流服务获取。
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }
}
