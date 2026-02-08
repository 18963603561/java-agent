package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型消息构造器。
 *
 * <p>用途：统一消息降级与协议映射，避免适配器层重复实现。</p>
 */
@Component
public class ModelMessageBuilder {

    /**
     * 构建兼容接口消息。
     *
     * @param definition 模型定义
     * @param messages 原始消息
     * @param prompt 兜底提示词
     * @return 消息列表
     */
    public List<Map<String, Object>> buildOpenAiMessages(ModelDefinition definition,
                                                         List<PromptMessage> messages,
                                                         String prompt) {
        if (messages != null && !messages.isEmpty()) {
            List<Map<String, Object>> payload = new ArrayList<>();
            List<PromptMessage> normalized = normalizeMessages(definition, messages);
            for (PromptMessage message : normalized) {
                if (message == null) {
                    continue;
                }
                payload.add(Map.of(
                        "role", toOpenAiRole(message.getRole()),
                        "content", message.getContent() == null ? "" : message.getContent()
                ));
            }
            return payload;
        }
        return List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt));
    }

    /**
     * 构建原生接口消息。
     *
     * @param messages 原始消息
     * @param prompt 兜底提示词
     * @return 消息列表
     */
    public List<Map<String, Object>> buildOllamaMessages(List<PromptMessage> messages, String prompt) {
        if (messages != null && !messages.isEmpty()) {
            List<Map<String, Object>> payload = new ArrayList<>();
            for (PromptMessage message : messages) {
                if (message == null) {
                    continue;
                }
                payload.add(Map.of(
                        "role", toOllamaRole(message.getRole()),
                        "content", message.getContent() == null ? "" : message.getContent()
                ));
            }
            return payload;
        }
        return List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt));
    }

    /**
     * 将请求对象转为日志与兜底用提示词。
     *
     * @param prompt 显式提示词
     * @param messages 消息列表
     * @return 序列化提示词
     */
    public String resolvePrompt(String prompt, List<PromptMessage> messages) {
        if (prompt != null) {
            return prompt;
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (PromptMessage message : messages) {
            if (message == null) {
                continue;
            }
            builder.append(message.getRole() != null ? message.getRole().name() : "USER");
            builder.append(":");
            builder.append(message.getContent() == null ? "" : message.getContent());
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private List<PromptMessage> normalizeMessages(ModelDefinition definition, List<PromptMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        if (supportsDeveloperRole(definition)) {
            return messages;
        }
        StringBuilder systemBuilder = new StringBuilder();
        List<PromptMessage> normalized = new ArrayList<>();
        for (PromptMessage message : messages) {
            if (message == null) {
                continue;
            }
            PromptRole role = message.getRole();
            String content = message.getContent() == null ? "" : message.getContent();
            if (role == PromptRole.SYSTEM) {
                appendSystemText(systemBuilder, content);
                continue;
            }
            if (role == PromptRole.DEVELOPER) {
                appendDeveloperText(systemBuilder, content);
                continue;
            }
            normalized.add(message);
        }
        if (systemBuilder.length() > 0) {
            normalized.add(0, new PromptMessage(PromptRole.SYSTEM, systemBuilder.toString().trim()));
        }
        return normalized;
    }

    private boolean supportsDeveloperRole(ModelDefinition definition) {
        if (definition == null) {
            return true;
        }
        Boolean supports = definition.getSupportsDeveloperRole();
        return supports == null || supports;
    }

    private void appendSystemText(StringBuilder builder, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(content.trim());
    }

    private void appendDeveloperText(StringBuilder builder, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append("[DEVELOPER]\n");
        builder.append(content.trim());
    }

    private String toOpenAiRole(PromptRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "developer";
            case USER -> "user";
        };
    }

    private String toOllamaRole(PromptRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "system";
            case USER -> "user";
        };
    }
}

