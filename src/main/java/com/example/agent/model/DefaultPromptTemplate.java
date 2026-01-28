package com.example.agent.model;

import com.example.agent.context.ContextSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认提示词模板，输出系统与开发者消息。
 */
@Component
public class DefaultPromptTemplate implements PromptTemplate {

    @Value("${agent.prompt.system:你是智能体运行时执行器，必须遵守安全边界与多租户隔离。}")
    private String systemMessage;

    @Value("${agent.prompt.developer:输出必须结构化且可追溯，遇到不确定先检索再回答。}")
    private String developerMessage;

    @Override
    public List<PromptMessage> render(ContextSnapshot snapshot) {
        List<PromptMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemMessage)) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, systemMessage));
        }
        String developer = buildDeveloperMessage(snapshot);
        if (StringUtils.hasText(developer)) {
            messages.add(new PromptMessage(PromptRole.DEVELOPER, developer));
        }
        return messages;
    }

    @Override
    public String getTemplateId() {
        return "default";
    }

    private String buildDeveloperMessage(ContextSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(developerMessage)) {
            builder.append(developerMessage.trim());
        }
        if (snapshot != null && snapshot.getRoleBoundary() != null
                && StringUtils.hasText(snapshot.getRoleBoundary().getRiskLevel())) {
            builder.append("\n");
            builder.append("风险等级:").append(snapshot.getRoleBoundary().getRiskLevel());
        }
        return builder.toString().trim();
    }
}