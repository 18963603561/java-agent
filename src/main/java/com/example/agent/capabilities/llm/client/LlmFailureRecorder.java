package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.provider.ProviderErrorMapper;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.observability.MetricsPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * LLM 失败记录器。
 *
 * <p>用途：统一构造失败执行结果并记录错误指标，避免调用编排层散落错误处理细节。
 * <p>输入：错误上下文与业务异常。
 * <p>输出：标准化失败结果。
 */
@Component
public class LlmFailureRecorder {

    private final ProviderErrorMapper providerErrorMapper;
    private final MetricsPublisher metricsPublisher;

    public LlmFailureRecorder(ProviderErrorMapper providerErrorMapper,
                              ObjectProvider<MetricsPublisher> metricsPublisherProvider) {
        this.providerErrorMapper = providerErrorMapper;
        this.metricsPublisher = metricsPublisherProvider != null ? metricsPublisherProvider.getIfAvailable() : null;
    }

    /**
     * 构建失败结果并落指标。
     *
     * @param traceId 链路标识
     * @param workflowId 工作流标识
     * @param scene 调用场景
     * @param provider 提供商
     * @param modelId 模型标识
     * @param exception 业务异常
     * @return 失败执行结果
     */
    public LlmExecutionResult buildFailureResult(String traceId,
                                                 String workflowId,
                                                 ModelScene scene,
                                                 String provider,
                                                 String modelId,
                                                 ErrorCodeException exception) {
        String errorCode = exception != null ? exception.getErrorCode() : null;
        String errorMessage = exception != null ? exception.getReason() : "模型调用失败";
        boolean retriable = providerErrorMapper.isRetriable(errorCode);
        LlmExecutionFailure failure = new LlmExecutionFailure(
                errorCode,
                errorMessage,
                exception != null ? exception.getClass().getSimpleName() : null,
                retriable);
        recordFailureMetrics(errorCode, provider, modelId, retriable);
        return LlmExecutionResult.failed(
                traceId,
                scene != null ? scene.name() : null,
                workflowId,
                provider,
                modelId,
                failure);
    }

    private void recordFailureMetrics(String errorCode, String provider, String modelId, boolean retriable) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags("llm_invocation_error_total",
                "errorCode", normalizeTag(errorCode),
                "provider", normalizeTag(provider),
                "model", normalizeTag(modelId),
                "retriable", String.valueOf(retriable));
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}

