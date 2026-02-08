package com.example.agent.capabilities.llm.client.events;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * LLM 事件载荷映射器。
 *
 * <p>用途：统一将事件 DTO 转换为事件总线需要的 payload Map。
 * <p>输入：提示词事件 DTO、解析事件 DTO。
 * <p>输出：可直接用于 {@code StreamEvent.payload} 的 Map。
 * <p>边界：当字段为空时自动忽略，避免污染下游契约。
 */
@Component
public class LlmEventPayloadMapper {

    /**
     * 将提示词事件 DTO 映射为 payload。
     *
     * @param payload 提示词事件 DTO
     * @return 事件 payload
     */
    public Map<String, Object> toMap(LlmPromptEventPayload payload) {
        if (payload == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "phase", payload.getPhase());
        putIfNotNull(map, "scene", payload.getScene());
        putIfNotNull(map, "provider", payload.getProvider());
        putIfNotNull(map, "modelId", payload.getModelId());
        putIfNotNull(map, "workflowId", payload.getWorkflowId());
        putIfNotNull(map, "traceId", payload.getTraceId());
        putIfNotNull(map, "status", payload.getStatus());
        putIfNotNull(map, "rawRef", payload.getRawRef());
        putIfNotNull(map, "prompt", payload.getPrompt());
        putIfNotNull(map, "messageCount", payload.getMessageCount());
        if (!payload.getMessageRoles().isEmpty()) {
            map.put("messageRoles", new ArrayList<>(payload.getMessageRoles()));
        }
        if (payload.getPromptTrace() != null) {
            map.putAll(payload.getPromptTrace().toPayload());
        }
        putAllIfPresent(map, payload.getMetadata());
        return map;
    }

    /**
     * 将解析事件 DTO 映射为 payload。
     *
     * @param payload 解析事件 DTO
     * @return 事件 payload
     */
    public Map<String, Object> toMap(LlmParseEventPayload payload) {
        if (payload == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "phase", payload.getPhase());
        putIfNotNull(map, "scene", payload.getScene());
        putIfNotNull(map, "provider", payload.getProvider());
        putIfNotNull(map, "modelId", payload.getModelId());
        putIfNotNull(map, "workflowId", payload.getWorkflowId());
        putIfNotNull(map, "traceId", payload.getTraceId());
        putIfNotNull(map, "status", payload.getStatus());
        putIfNotNull(map, "rawRef", payload.getRawRef());
        putIfNotNull(map, "content", payload.getContent());
        putIfNotNull(map, "inputTokens", payload.getInputTokens());
        putIfNotNull(map, "outputTokens", payload.getOutputTokens());
        putIfNotNull(map, "totalTokens", payload.getTotalTokens());
        putIfNotNull(map, "errorCode", payload.getErrorCode());
        putIfNotNull(map, "errorMessage", payload.getErrorMessage());
        putIfNotNull(map, "exceptionType", payload.getExceptionType());
        putIfNotNull(map, "retriable", payload.getRetriable());
        if (payload.getPromptTrace() != null) {
            map.putAll(payload.getPromptTrace().toPayload());
        }
        putAllIfPresent(map, payload.getMetadata());
        return map;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target == null || key == null || value == null) {
            return;
        }
        target.put(key, value);
    }

    private void putAllIfPresent(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            target.put(entry.getKey(), entry.getValue());
        }
    }
}
