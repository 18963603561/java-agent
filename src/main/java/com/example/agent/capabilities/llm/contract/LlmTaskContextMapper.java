package com.example.agent.capabilities.llm.contract;

import com.example.agent.api.http.dto.TaskRequest;
import java.util.Map;

/**
 * LLM 任务上下文适配器。
 *
 * <p>用途：将入口层 {@link TaskRequest} 适配为 LLM 能力层上下文模型，避免能力层直接依赖入口 DTO。
 */
public class LlmTaskContextMapper {

    /**
     * 从任务请求构建 LLM 上下文。
     *
     * @param taskRequest 任务请求
     * @return LLM 任务上下文
     */
    public static LlmTaskContext fromTaskRequest(TaskRequest taskRequest) {
        if (taskRequest == null) {
            return LlmTaskContext.empty();
        }
        Map<String, Object> context = taskRequest.getContext();
        String tenantId = resolveTenantId(context);
        return new LlmTaskContext(
                tenantId,
                taskRequest.getSkillName(),
                context,
                taskRequest.getToolChoice());
    }

    private static String resolveTenantId(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get("tenantId");
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text == null ? null : text.trim();
    }
}
