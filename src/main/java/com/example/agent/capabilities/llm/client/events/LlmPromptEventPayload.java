package com.example.agent.capabilities.llm.client.events;

import com.example.agent.capabilities.llm.prompt.PromptTrace;
import java.util.List;
import java.util.Map;

/**
 * LLM 提示词事件载荷。
 *
 * <p>用途：承载提示词事件字段，替代服务内散落的 payload put 操作。
 * <p>输入：阶段、场景、模型、提示词、消息角色与元数据。
 * <p>输出：供事件映射器统一转换为 Map。
 * <p>边界：字段允许为空，映射器负责过滤空值与协议收口。
 */
public class LlmPromptEventPayload {

    private final String phase;
    private final String scene;
    private final String provider;
    private final String modelId;
    private final String workflowId;
    private final String traceId;
    private final String status;
    private final String rawRef;
    private final String prompt;
    private final Integer messageCount;
    private final List<String> messageRoles;
    private final PromptTrace promptTrace;
    private final Map<String, Object> metadata;

    public LlmPromptEventPayload(String phase,
                                 String scene,
                                 String provider,
                                 String modelId,
                                 String workflowId,
                                 String traceId,
                                 String status,
                                 String rawRef,
                                 String prompt,
                                 Integer messageCount,
                                 List<String> messageRoles,
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
        this.prompt = prompt;
        this.messageCount = messageCount;
        this.messageRoles = messageRoles == null ? List.of() : List.copyOf(messageRoles);
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

    public String getPrompt() {
        return prompt;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public List<String> getMessageRoles() {
        return messageRoles;
    }

    public PromptTrace getPromptTrace() {
        return promptTrace;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
