package com.example.agent.capabilities.llm.prompt;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 提示词装配上下文解析器。
 *
 * <p>用途：统一解析上下文快照与装配输入，并补齐 system/developer/user 字段。
 * <p>输入：任务请求、步骤输入、用户提示。
 * <p>输出：可供裁剪与消息组装的完整装配输入。
 */
class PromptAssemblyContextResolver {

    private final PromptTemplate promptTemplate;

    PromptAssemblyContextResolver(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    /**
     * 解析上下文快照，优先步骤输入。
     *
     * @param taskRequest 任务请求
     * @param stepInput 步骤输入
     * @return 上下文快照
     */
    ContextSnapshot resolveSnapshot(TaskRequest taskRequest, Map<String, Object> stepInput) {
        if (stepInput != null) {
            Object snapshot = stepInput.get("contextSnapshot");
            if (snapshot instanceof ContextSnapshot value) {
                return value;
            }
        }
        if (taskRequest != null && taskRequest.getContext() != null) {
            Object snapshot = taskRequest.getContext().get("contextSnapshot");
            if (snapshot instanceof ContextSnapshot value) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析提示装配输入，优先步骤输入。
     *
     * @param stepInput 步骤输入
     * @param taskRequest 任务请求
     * @return 装配输入
     */
    PromptAssemblyInput resolveAssemblyInput(Map<String, Object> stepInput, TaskRequest taskRequest) {
        if (stepInput != null) {
            Object value = stepInput.get("promptAssemblyInput");
            if (value instanceof PromptAssemblyInput input) {
                return input;
            }
        }
        if (taskRequest != null && taskRequest.getContext() != null) {
            Object value = taskRequest.getContext().get("promptAssemblyInput");
            if (value instanceof PromptAssemblyInput input) {
                return input;
            }
        }
        return null;
    }

    /**
     * 补齐装配输入的 system/developer/user 等字段。
     *
     * @param input 装配输入
     * @param snapshot 上下文快照
     * @param prompt 用户输入
     * @return 补齐后的输入
     */
    PromptAssemblyInput enrichAssemblyInput(PromptAssemblyInput input,
                                            ContextSnapshot snapshot,
                                            String prompt) {
        if (input == null) {
            return null;
        }
        if (!StringUtils.hasText(input.getSystemText()) || !StringUtils.hasText(input.getDeveloperText())) {
            fillSystemDeveloper(input, snapshot);
        }
        if (!StringUtils.hasText(input.getUserText())) {
            input.setUserText(prompt);
        }
        if (input.getTruncatedSections() == null) {
            input.setTruncatedSections(new ArrayList<>());
        }
        return input;
    }

    private void fillSystemDeveloper(PromptAssemblyInput input, ContextSnapshot snapshot) {
        if (promptTemplate == null || input == null) {
            return;
        }
        List<PromptMessage> messages = promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot));
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if (message.getRole() == PromptRole.SYSTEM && !StringUtils.hasText(input.getSystemText())) {
                input.setSystemText(message.getContent());
            }
            if (message.getRole() == PromptRole.DEVELOPER && !StringUtils.hasText(input.getDeveloperText())) {
                input.setDeveloperText(message.getContent());
            }
        }
    }
}

