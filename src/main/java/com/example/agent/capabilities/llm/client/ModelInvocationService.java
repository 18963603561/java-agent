package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.client.events.LlmInvocationMetadata;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.provider.ProviderErrorMapper;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 模型调用服务。
 *
 * <p>用途：编排模型路由、调用与统一结果事件发布。
 * <p>输入：模型请求、场景、租户上下文、工作流标识、阶段与元数据。
 * <p>输出：模型响应对象。
 * <p>边界：关键入参缺失时使用安全默认值；异常统一映射为标准错误码。
 */
@Service
public class ModelInvocationService {

    private static final Logger log = LoggerFactory.getLogger(ModelInvocationService.class);

    private final LlmClient llmClient;
    private final ModelRouter modelRouter;
    private final ValidationSupport validationSupport;
    private final ProviderErrorMapper providerErrorMapper;
    private final RawRefAttachmentService rawRefAttachmentService;
    private final LlmEventPublisher llmEventPublisher;
    private final LlmFailureRecorder llmFailureRecorder;

    @Value("${agent.llm.event.publish-enabled:true}")
    private boolean llmEventPublishEnabled;

    public ModelInvocationService(LlmClient llmClient,
                                  ModelRouter modelRouter,
                                  ValidationSupport validationSupport,
                                  ProviderErrorMapper providerErrorMapper,
                                  RawRefAttachmentService rawRefAttachmentService,
                                  LlmEventPublisher llmEventPublisher,
                                  LlmFailureRecorder llmFailureRecorder) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.validationSupport = validationSupport;
        this.providerErrorMapper = providerErrorMapper;
        this.rawRefAttachmentService = rawRefAttachmentService;
        this.llmEventPublisher = llmEventPublisher;
        this.llmFailureRecorder = llmFailureRecorder;
    }

    /**
     * 调用模型并发布提示词与输出事件。
     *
     * @param request 模型请求
     * @param scene 场景
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号计数器
     * @param phase 阶段标识
     * @param metadata 元数据
     * @return 模型响应
     */
    public ModelResponse invoke(ModelRequest request,
                                ModelScene scene,
                                TenantContext tenantContext,
                                String workflowId,
                                AtomicLong seqCounter,
                                String phase,
                                Map<String, Object> metadata) {
        ModelScene resolvedScene = scene != null ? scene : ModelScene.CHEAP;
        String resolvedPhase = validationSupport.normalizeText(phase, "unknown");
        ModelRequest safeRequest = request != null ? request : new ModelRequest();
        safeRequest.setScene(resolvedScene);
        ModelDefinition definition = modelRouter.route(resolvedScene);
        String modelId = definition != null ? definition.getModelId() : null;
        String provider = definition != null ? definition.getProvider() : null;
        String traceId = tenantContext != null ? tenantContext.getTraceId() : null;

        Map<String, Object> runtimeMetadata = metadata != null ? new java.util.LinkedHashMap<>(metadata)
                : new java.util.LinkedHashMap<>();
        runtimeMetadata.putIfAbsent("scene", resolvedScene.name());
        if (provider != null && !provider.isBlank()) {
            runtimeMetadata.putIfAbsent("provider", provider);
        }
        String promptScene = resolvePromptScene(resolvedPhase, runtimeMetadata);
        PromptTrace trace = PromptTrace.fromPrompt(promptScene, safeRequest.getPrompt());
        runtimeMetadata.put("promptTrace", trace);

        llmEventPublisher.publishPromptEvent(llmEventPublishEnabled,
                tenantContext,
                workflowId,
                seqCounter,
                resolvedPhase,
                modelId,
                safeRequest,
                runtimeMetadata);

        long startNs = System.nanoTime();
        try {
            log.info("模型调用开始, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    modelId,
                    resolvedPhase);
            ModelResponse response = llmClient.generate(safeRequest);
            rawRefAttachmentService.attach(response, resolvedScene, resolvedPhase);
            LlmExecutionResult executionResult = LlmExecutionResult.success(
                    traceId,
                    resolvedScene.name(),
                    workflowId,
                    provider,
                    response);
            llmEventPublisher.publishParseEvent(llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    resolvedPhase,
                    executionResult,
                    runtimeMetadata);
            log.info("模型调用完成, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, latencyMs={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    response != null ? response.getModelId() : modelId,
                    resolvedPhase,
                    (System.nanoTime() - startNs) / 1_000_000);
            return response;
        } catch (ErrorCodeException ex) {
            LlmExecutionResult executionResult = llmFailureRecorder.buildFailureResult(traceId,
                    workflowId,
                    resolvedScene,
                    provider,
                    modelId,
                    ex);
            llmEventPublisher.publishParseEvent(llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    resolvedPhase,
                    executionResult,
                    runtimeMetadata);
            boolean retriable = executionResult.getFailure() != null && executionResult.getFailure().isRetriable();
            log.error("模型调用失败, tenantId={}, workflowId={}, scene={}, phase={}, traceId={}, provider={}, modelId={}, errorCode={}, retriable={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    resolvedPhase,
                    traceId,
                    provider,
                    modelId,
                    ex.getErrorCode(),
                    retriable,
                    ex);
            throw ex;
        } catch (Exception ex) {
            ErrorCodeException mapped = providerErrorMapper.mapThrowable(ex);
            LlmExecutionResult executionResult = llmFailureRecorder.buildFailureResult(traceId,
                    workflowId,
                    resolvedScene,
                    provider,
                    modelId,
                    mapped);
            llmEventPublisher.publishParseEvent(llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    resolvedPhase,
                    executionResult,
                    runtimeMetadata);
            boolean retriable = executionResult.getFailure() != null && executionResult.getFailure().isRetriable();
            log.error("模型调用异常, tenantId={}, workflowId={}, scene={}, phase={}, traceId={}, provider={}, modelId={}, errorCode={}, retriable={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    resolvedScene,
                    resolvedPhase,
                    traceId,
                    provider,
                    modelId,
                    mapped.getErrorCode(),
                    retriable,
                    ex);
            throw mapped;
        }
    }

    /**
     * 调用模型并使用强类型元数据。
     *
     * @param request 模型请求
     * @param scene 场景
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号计数器
     * @param phase 阶段标识
     * @param metadata 调用元数据对象
     * @return 模型响应
     */
    public ModelResponse invokeWithMetadata(ModelRequest request,
                                            ModelScene scene,
                                            TenantContext tenantContext,
                                            String workflowId,
                                            AtomicLong seqCounter,
                                            String phase,
                                            LlmInvocationMetadata metadata) {
        Map<String, Object> runtimeMetadata = metadata != null ? metadata.toMetadataMap() : Map.of();
        return invoke(request, scene, tenantContext, workflowId, seqCounter, phase, runtimeMetadata);
    }

    /**
     * 记录提示词追踪信息并发布事件。
     *
     * @param trace 追踪信息
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 序号计数器
     * @param phase 阶段
     * @param modelId 模型标识
     */
    public void recordPromptTrace(PromptTrace trace,
                                  TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
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
        llmEventPublisher.publishPromptTraceEvent(llmEventPublishEnabled,
                trace,
                tenantContext,
                workflowId,
                seqCounter,
                phase,
                modelId);
    }

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
}

