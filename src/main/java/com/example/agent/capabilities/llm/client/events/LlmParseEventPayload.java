package com.example.agent.capabilities.llm.client.events;

import com.example.agent.capabilities.llm.prompt.PromptTrace;
import java.util.Map;

/**
 * LLM 解析事件载荷。
 *
 * <p>用途：承载模型输出解析事件字段，替代服务内散落的 payload put 操作。
 * <p>输入：阶段、模型标识、输出内容、token 信息与元数据。
 * <p>输出：供事件映射器统一转换为 Map。
 * <p>边界：字段允许为空，映射器负责过滤空值与协议收口。
 */
public class LlmParseEventPayload {

    private final String phase;
    private final String scene;
    private final String provider;
    private final String modelId;
    private final String workflowId;
    private final String traceId;
    private final String status;
    private final String rawRef;
    private final String content;
    private final Integer inputTokens;
    private final Integer outputTokens;
    private final Integer totalTokens;
    private final String errorCode;
    private final String errorMessage;
    private final String exceptionType;
    private final Boolean retriable;
    private final PromptTrace promptTrace;
    private final Map<String, Object> metadata;

    public LlmParseEventPayload(String phase,
                                String scene,
                                String provider,
                                String modelId,
                                String workflowId,
                                String traceId,
                                String status,
                                String rawRef,
                                String content,
                                Integer inputTokens,
                                Integer outputTokens,
                                Integer totalTokens,
                                String errorCode,
                                String errorMessage,
                                String exceptionType,
                                Boolean retriable,
                                PromptTrace promptTrace,
                                Map<String, Object> metadata) {
        this.phase = phase;
        this.scene = scene;
        this.provider = provider;
        this.modelId = modelId;
        this.workflowId = workflowId;
        this.traceId = traceId;
        this.status = status;
        this.rawRef = rawRef;
        this.content = content;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.retriable = retriable;
        this.promptTrace = promptTrace;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getPhase() {
        return phase;
    }

    public String getScene() {
        return scene;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProvider() {
        return provider;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getStatus() {
        return status;
    }

    public String getRawRef() {
        return rawRef;
    }

    public String getContent() {
        return content;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public Boolean getRetriable() {
        return retriable;
    }

    public PromptTrace getPromptTrace() {
        return promptTrace;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
