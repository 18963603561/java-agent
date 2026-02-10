package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.client.events.LlmInvocationMetadata;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.provider.ProviderErrorMapper;
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
    private final ModelInvocationContextFactory invocationContextFactory;
    private final ModelInvocationTelemetry invocationTelemetry;
    private final ProviderErrorMapper providerErrorMapper;
    private final RawRefAttachmentService rawRefAttachmentService;
    private final LlmEventPublisher llmEventPublisher;

    @Value("${agent.llm.event.publish-enabled:true}")
    private boolean llmEventPublishEnabled;

    public ModelInvocationService(LlmClient llmClient,
                                  ModelInvocationContextFactory invocationContextFactory,
                                  ModelInvocationTelemetry invocationTelemetry,
                                  ProviderErrorMapper providerErrorMapper,
                                  RawRefAttachmentService rawRefAttachmentService,
                                  LlmEventPublisher llmEventPublisher) {
        this.llmClient = llmClient;
        this.invocationContextFactory = invocationContextFactory;
        this.invocationTelemetry = invocationTelemetry;
        this.providerErrorMapper = providerErrorMapper;
        this.rawRefAttachmentService = rawRefAttachmentService;
        this.llmEventPublisher = llmEventPublisher;
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
        ModelInvocationContext invocationContext = invocationContextFactory.create(
                request,
                scene,
                phase,
                tenantContext,
                metadata);
        invocationTelemetry.publishPromptEvent(
                llmEventPublishEnabled,
                tenantContext,
                workflowId,
                seqCounter,
                invocationContext);

        try {
            log.info("模型调用开始, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    invocationContext.getScene(),
                    invocationContext.getModelId(),
                    invocationContext.getPhase());
            ModelResponse response = llmClient.generate(invocationContext.getRequest());
            rawRefAttachmentService.attach(response, invocationContext.getScene(), invocationContext.getPhase());
            invocationTelemetry.publishSuccess(
                    llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    invocationContext,
                    response);
            return response;
        } catch (ErrorCodeException ex) {
            invocationTelemetry.publishFailure(
                    llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    invocationContext,
                    ex,
                    ex);
            throw ex;
        } catch (Exception ex) {
            ErrorCodeException mapped = providerErrorMapper.mapThrowable(ex);
            invocationTelemetry.publishFailure(
                    llmEventPublishEnabled,
                    tenantContext,
                    workflowId,
                    seqCounter,
                    invocationContext,
                    mapped,
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
}
