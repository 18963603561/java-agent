package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 模型调用观测发布器。
 *
 * <p>用途：统一承接模型调用日志与事件发布，避免调用服务内观测逻辑散落。
 */
@Component
public class ModelInvocationTelemetry {

    private static final Logger log = LoggerFactory.getLogger(ModelInvocationTelemetry.class);

    private final LlmEventPublisher llmEventPublisher;
    private final LlmFailureRecorder llmFailureRecorder;

    public ModelInvocationTelemetry(LlmEventPublisher llmEventPublisher,
                                    LlmFailureRecorder llmFailureRecorder) {
        this.llmEventPublisher = llmEventPublisher;
        this.llmFailureRecorder = llmFailureRecorder;
    }

    /**
     * 发布提示词事件。
     *
     * @param publishEnabled 是否启用发布
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @param invocationContext 调用上下文
     */
    public void publishPromptEvent(boolean publishEnabled,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   ModelInvocationContext invocationContext) {
        if (invocationContext == null) {
            return;
        }
        llmEventPublisher.publishPromptEvent(
                publishEnabled,
                tenantContext,
                workflowId,
                seqCounter,
                invocationContext.getPhase(),
                invocationContext.getModelId(),
                invocationContext.getRequest(),
                invocationContext.getMetadata());
    }

    /**
     * 发布成功结果并记录完成日志。
     *
     * @param publishEnabled 是否启用发布
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @param invocationContext 调用上下文
     * @param response 模型响应
     */
    public void publishSuccess(boolean publishEnabled,
                               TenantContext tenantContext,
                               String workflowId,
                               AtomicLong seqCounter,
                               ModelInvocationContext invocationContext,
                               ModelResponse response) {
        if (invocationContext == null) {
            return;
        }
        LlmExecutionResult executionResult = LlmExecutionResult.success(
                invocationContext.getTraceId(),
                invocationContext.getScene().name(),
                workflowId,
                invocationContext.getProvider(),
                response);
        llmEventPublisher.publishParseEvent(
                publishEnabled,
                tenantContext,
                workflowId,
                seqCounter,
                invocationContext.getPhase(),
                executionResult,
                invocationContext.getMetadata());
        log.info("模型调用完成, tenantId={}, workflowId={}, scene={}, modelId={}, phase={}, latencyMs={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                invocationContext.getScene(),
                response != null ? response.getModelId() : invocationContext.getModelId(),
                invocationContext.getPhase(),
                (System.nanoTime() - invocationContext.getStartNs()) / 1_000_000);
    }

    /**
     * 发布失败结果并记录失败日志。
     *
     * @param publishEnabled 是否启用发布
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号
     * @param invocationContext 调用上下文
     * @param exception 业务异常
     * @param throwable 原始异常
     */
    public void publishFailure(boolean publishEnabled,
                               TenantContext tenantContext,
                               String workflowId,
                               AtomicLong seqCounter,
                               ModelInvocationContext invocationContext,
                               ErrorCodeException exception,
                               Throwable throwable) {
        if (invocationContext == null) {
            return;
        }
        LlmExecutionResult executionResult = llmFailureRecorder.buildFailureResult(
                invocationContext.getTraceId(),
                workflowId,
                invocationContext.getScene(),
                invocationContext.getProvider(),
                invocationContext.getModelId(),
                exception);
        llmEventPublisher.publishParseEvent(
                publishEnabled,
                tenantContext,
                workflowId,
                seqCounter,
                invocationContext.getPhase(),
                executionResult,
                invocationContext.getMetadata());
        boolean retriable = executionResult.getFailure() != null && executionResult.getFailure().isRetriable();
        log.error("模型调用失败, tenantId={}, workflowId={}, scene={}, phase={}, traceId={}, provider={}, modelId={}, errorCode={}, retriable={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                workflowId,
                invocationContext.getScene(),
                invocationContext.getPhase(),
                invocationContext.getTraceId(),
                invocationContext.getProvider(),
                invocationContext.getModelId(),
                exception != null ? exception.getErrorCode() : null,
                retriable,
                throwable);
    }
}

