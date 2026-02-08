package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelResponse;

/**
 * LLM 统一执行结果协议。
 *
 * <p>用途：统一承载模型调用的成功结果、失败信息与链路标识。
 * <p>输入：模型调用服务在成功或失败分支构建。
 * <p>输出：供事件、日志、指标和上层业务统一消费。
 */
public class LlmExecutionResult {

    /**
     * 追踪标识。
     */
    private final String traceId;

    /**
     * 场景名称。
     */
    private final String scene;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 提供商。
     */
    private final String provider;

    /**
     * 模型标识。
     */
    private final String modelId;

    /**
     * 模型原始文本。
     */
    private final String rawText;

    /**
     * 原始输出引用。
     */
    private final String rawRef;

    /**
     * 调用状态。
     */
    private final LlmResultStatus status;

    /**
     * 令牌消耗。
     */
    private final LlmExecutionUsage usage;

    /**
     * 失败信息。
     */
    private final LlmExecutionFailure failure;

    /**
     * 构造统一执行结果。
     *
     * @param traceId 追踪标识
     * @param scene 场景
     * @param workflowId 工作流标识
     * @param provider 提供商
     * @param modelId 模型标识
     * @param rawText 原始文本
     * @param rawRef 原始引用
     * @param status 调用状态
     * @param usage 令牌消耗
     * @param failure 失败信息
     */
    public LlmExecutionResult(String traceId,
                              String scene,
                              String workflowId,
                              String provider,
                              String modelId,
                              String rawText,
                              String rawRef,
                              LlmResultStatus status,
                              LlmExecutionUsage usage,
                              LlmExecutionFailure failure) {
        this.traceId = traceId;
        this.scene = scene;
        this.workflowId = workflowId;
        this.provider = provider;
        this.modelId = modelId;
        this.rawText = rawText;
        this.rawRef = rawRef;
        this.status = status;
        this.usage = usage;
        this.failure = failure;
    }

    /**
     * 创建成功结果。
     *
     * @param traceId 追踪标识
     * @param scene 场景
     * @param workflowId 工作流标识
     * @param provider 提供商
     * @param response 模型响应
     * @return 统一执行结果
     */
    public static LlmExecutionResult success(String traceId,
                                             String scene,
                                             String workflowId,
                                             String provider,
                                             ModelResponse response) {
        String modelId = response != null ? response.getModelId() : null;
        String rawText = response != null ? response.getContent() : null;
        String rawRef = response != null ? response.getRawRef() : null;
        LlmExecutionUsage usage = response == null
                ? LlmExecutionUsage.of(0, 0)
                : LlmExecutionUsage.of(response.getInputTokens(), response.getOutputTokens());
        return new LlmExecutionResult(traceId,
                scene,
                workflowId,
                provider,
                modelId,
                rawText,
                rawRef,
                LlmResultStatus.SUCCESS,
                usage,
                null);
    }

    /**
     * 创建失败结果。
     *
     * @param traceId 追踪标识
     * @param scene 场景
     * @param workflowId 工作流标识
     * @param provider 提供商
     * @param modelId 模型标识
     * @param failure 失败信息
     * @return 统一执行结果
     */
    public static LlmExecutionResult failed(String traceId,
                                            String scene,
                                            String workflowId,
                                            String provider,
                                            String modelId,
                                            LlmExecutionFailure failure) {
        LlmResultStatus status = failure != null && failure.isRetriable()
                ? LlmResultStatus.RETRIABLE_FAILED
                : LlmResultStatus.FAILED;
        return new LlmExecutionResult(traceId,
                scene,
                workflowId,
                provider,
                modelId,
                null,
                null,
                status,
                LlmExecutionUsage.of(0, 0),
                failure);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getScene() {
        return scene;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelId() {
        return modelId;
    }

    public String getRawText() {
        return rawText;
    }

    public String getRawRef() {
        return rawRef;
    }

    public LlmResultStatus getStatus() {
        return status;
    }

    public LlmExecutionUsage getUsage() {
        return usage;
    }

    public LlmExecutionFailure getFailure() {
        return failure;
    }
}

