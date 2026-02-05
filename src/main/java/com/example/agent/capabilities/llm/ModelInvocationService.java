package com.example.agent.capabilities.llm;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.runtime.raw.RawRef;
import com.example.agent.runtime.raw.RawResultStore;
import com.example.agent.streaming.sse.EventStreamService;
import com.example.agent.capabilities.llm.PromptTrace;
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
 * 模型调用服务，负责模型路由、调用与事件发布。
 * <p>用途：封装模型调用链路，统一记录提示词与输出事件。
 * <p>输入：模型请求、场景、租户上下文、工作流标识、阶段与元数据。
 * <p>输出：模型响应对象。
 * <p>边界：调用失败时抛出业务异常或封装为模型不可用。
 * <p>示例：
 * <pre>{@code
 * ModelResponse response = invoke(request, scene, tenantContext, workflowId, seqCounter, "plan", Map.of());
 * }</pre>
 */
@Service
public class ModelInvocationService {

    /**
     * 日志记录器。
     * <p>示例：记录模型调用开始与完成。
     */
    private static final Logger log = LoggerFactory.getLogger(ModelInvocationService.class);

    /**
     * 模型客户端。
     * <p>示例：执行 {@code LlmClient.generate} 获取响应。
     */
    private final LlmClient llmClient;
    /**
     * 模型路由器。
     * <p>示例：根据场景选择模型定义。
     */
    private final ModelRouter modelRouter;
    /**
     * 事件发布器。
     * <p>示例：发布提示词与输出事件。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 事件流服务。
     * <p>示例：生成事件序列号。
     */
    private final EventStreamService eventStreamService;
    /**
     * 原始结果存储器。
     * <p>示例：为模型原始输出生成 {@code rawRef}。
     */
    private final RawResultStore rawResultStore;

    /**
     * 构造模型调用服务。
     *
     * <p>输入：模型客户端、路由器、事件发布器与事件流服务。
     * <p>输出：初始化后的模型调用服务。
     * <p>示例：
     * <pre>{@code
     * new ModelInvocationService(llmClient, modelRouter, eventPublisher, eventStreamService);
     * }</pre>
     *
     * @param llmClient 模型客户端
     * @param modelRouter 模型路由器
     * @param eventPublisher 事件发布器
     * @param eventStreamService 事件流服务
     */
    public ModelInvocationService(LlmClient llmClient,
                                  ModelRouter modelRouter,
                                  ApplicationEventPublisher eventPublisher,
                                  EventStreamService eventStreamService,
                                  RawResultStore rawResultStore) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.rawResultStore = rawResultStore;
    }

    /**
     * 调用模型并发布提示词与输出事件。
     *
     * <p>输入：模型请求、场景、租户上下文、工作流标识、序列计数器、阶段与元数据。
     * <p>输出：模型响应对象。
     * <p>边界：业务异常透传；其他异常封装为模型不可用。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = invoke(request, ModelScene.PLANNER, ctx, wfId, seq, "plan", Map.of("planId","p1"));
     * }</pre>
     *
     * @param request 模型请求
     * @param scene 场景
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序列计数器，可为空
     * @param phase 阶段标识
     * @param metadata 运行时元数据
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
        Map<String, Object> runtimeMetadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        String promptScene = resolvePromptScene(phase, runtimeMetadata);
        PromptTrace trace = PromptTrace.fromPrompt(promptScene, safeRequest.getPrompt());
        runtimeMetadata.put("promptTrace", trace);

        // 发布提示词事件，记录请求上下文与追踪信息。
        publishPromptEvent(tenantContext, workflowId, seqCounter, phase, modelId, safeRequest, runtimeMetadata);

        long startNs = System.nanoTime();
        try {
            log.info("模型调用开始, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    modelId,
                    phase);
            // 调用模型生成结果。
            ModelResponse response = llmClient.generate(safeRequest);
            // 统一保存模型原始输出引用，便于后续链路追踪。
            attachRawRef(response, scene, phase);
            // 发布输出事件，记录模型返回内容与追踪信息。
            publishOutputEvent(tenantContext, workflowId, seqCounter, phase, response, runtimeMetadata);
            log.info("模型调用完成, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, latencyMs={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    scene,
                    response != null ? response.getModelId() : modelId,
                    phase,
                    (System.nanoTime() - startNs) / 1_000_000);
            return response;
        } catch (ErrorCodeException ex) {
            // 业务异常：记录错误码与上下文信息，交由上层处理。
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
            // 非预期异常：统一包装为模型不可用错误。
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
     * 发布提示词事件并补充追踪信息。
     *
     * <p>输入：租户上下文、工作流标识、序列计数器、阶段、模型标识、请求与元数据。
     * <p>输出：无。
     * <p>边界：租户上下文或工作流为空时不发布。
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
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace != null) {
            payload.putAll(trace.toPayload());
        }
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            payload.put("messageCount", request.getMessages().size());
            payload.put("messageRoles", request.getMessages().stream()
                    .map(message -> message != null && message.getRole() != null ? message.getRole().name() : "USER")
                    .toList());
        }
        if (metadata != null) {
            payload.putAll(metadata);
        }
        // 发布提示词事件，记录事件流信息。
        // publishEvent(tenantContext, workflowId, seq, EventType.LLM_PROMPT, payload);
    }

    /**
     * 发布输出解析事件。
     *
     * <p>输入：租户上下文、工作流标识、序列计数器、阶段、响应与元数据。
     * <p>输出：无。
     * <p>边界：租户上下文、工作流或响应为空时不发布。
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
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace != null) {
            payload.putAll(trace.toPayload());
        }
        if (metadata != null) {
            payload.putAll(metadata);
        }
        // 发布输出解析事件，记录事件流信息。
        // publishEvent(tenantContext, workflowId, seq, EventType.LLM_PARSE, payload);
    }

    /**
     * 发布统一事件记录。
     *
     * <p>输入：租户上下文、工作流标识、序列号、事件类型与载荷。
     * <p>输出：无。
     * <p>边界：调用方需保证必要参数非空。
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
     * 获取下一条事件序列号。
     *
     * <p>输入：租户上下文、工作流标识与可选计数器。
     * <p>输出：序列号。
     * <p>边界：优先使用计数器；为空时调用事件流服务生成。
     * <p>示例：
     * <pre>{@code
     * long seq = nextSeq(ctx, wfId, seqCounter);
     * }</pre>
     */
    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            // 优先使用外部序列计数器，便于统一排序。
            return seqCounter.incrementAndGet();
        }
        // 未传入计数器时由事件流服务生成序列号。
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
    }

    /**
     * 记录提示词追踪信息并发布事件。
     *
     * <p>输入：追踪信息、租户上下文、工作流标识、序列计数器、阶段与模型标识。
     * <p>输出：无。
     * <p>边界：追踪为空或租户/工作流为空时不发布。
     */
    public void recordPromptTrace(PromptTrace trace,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  java.util.concurrent.atomic.AtomicLong seqCounter,
                                  String phase,
                                  String modelId) {
        if (trace == null) {
            return;
        }
        log.info("提示词追踪记录, tenantId={}, workflowId={}, phase={}, modelId={}, promptScene={}, promptId={}, promptChars={}, tokensEstimate={}, parseSuccess={}, parseErrorType={}, repairAttempted={}, repairSuccess={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                phase,
                modelId,
                trace.getPromptScene(),
                trace.getPromptId(),
                trace.getPromptChars(),
                trace.getPromptTokensEstimate(),
                trace.getParseSuccess(),
                trace.getParseErrorType(),
                trace.getRepairAttempted(),
                trace.getRepairSuccess());
        if (tenantContext == null || workflowId == null) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
        Map<String, Object> payload = new HashMap<>();
        payload.put("phase", phase);
        payload.put("modelId", modelId);
        payload.putAll(trace.toPayload());
        publishEvent(tenantContext, workflowId, seq, EventType.LLM_OUTPUT, payload);
    }

    /**
     * 解析提示词场景名称。
     *
     * <p>输入：阶段标识与元数据。
     * <p>输出：提示词场景字符串。
     * <p>边界：元数据包含 {@code promptScene} 时优先使用；阶段为空时返回 {@code unknown}。
     */
    private String resolvePromptScene(String phase, Map<String, Object> metadata) {
        if (metadata != null) {
            Object value = metadata.get("promptScene");
            if (value instanceof String scene && !scene.isBlank()) {
                return scene.trim();
            }
        }
        if (phase == null) {
            return "unknown";
        }
        return switch (phase) {
            case "plan" -> "planner";
            case "reflect" -> "reflect";
            case "finalize" -> "final";
            case "react_think" -> "react";
            case "cot" -> "cot";
            case "research" -> "research";
            case "debate" -> "debate";
            case "multi_agent" -> "multiagent";
            case "json_repair" -> "repair";
            default -> phase;
        };
    }

    /**
     * 为模型输出挂载原始结果引用。
     *
     * <p>输入：模型响应、场景与阶段标识。
     * <p>输出：无。
     * <p>边界：响应为空、内容为空或存储器为空时跳过。
     *
     * @param response 模型响应
     * @param scene 调用场景
     * @param phase 阶段标识
     */
    private void attachRawRef(ModelResponse response, ModelScene scene, String phase) {
        if (response == null || rawResultStore == null) {
            return;
        }
        String content = response.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        String source = "model";
        if (scene != null) {
            source = source + ":" + scene.name().toLowerCase();
        }
        if (phase != null && !phase.isBlank()) {
            source = source + ":" + phase;
        }
        RawRef rawRef = rawResultStore.store(source, content, "text/plain");
        if (rawRef != null && rawRef.getKey() != null && !rawRef.getKey().isBlank()) {
            response.setRawRef(rawRef.getKey());
        }
    }
}
