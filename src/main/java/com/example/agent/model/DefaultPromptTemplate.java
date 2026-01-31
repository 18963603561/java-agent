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

    /**
     * 系统提示默认内容。
     */
    static final String DEFAULT_SYSTEM_MESSAGE = "你是智能体运行时执行器，必须遵守安全边界与多租户隔离。";

    /**
     * 开发者提示默认内容。
     */
    static final String DEFAULT_DEVELOPER_MESSAGE = "输出必须结构化且可追溯，遇到不确定先检索再回答。";

    @Value("${agent.prompt.system:" + DEFAULT_SYSTEM_MESSAGE + "}")
    private String systemMessage;

    @Value("${agent.prompt.developer:" + DEFAULT_DEVELOPER_MESSAGE + "}")
    private String developerMessage;

    /**
     * 提示词默认值解析器，用于处理空白配置。
     */
    private final PromptTemplateResolver promptTemplateResolver = new PromptTemplateResolver();

    /**
     * 渲染系统与开发者提示消息。
     *
     * @param snapshot 上下文快照
     * @return 提示消息列表
     */
    @Override
    public List<PromptMessage> render(ContextSnapshot snapshot) {
        List<PromptMessage> messages = new ArrayList<>();
        String resolvedSystem = promptTemplateResolver.resolveSystemMessage(systemMessage, DEFAULT_SYSTEM_MESSAGE);
        if (StringUtils.hasText(resolvedSystem)) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, resolvedSystem));
        }
        String developer = buildDeveloperMessage(snapshot);
        if (StringUtils.hasText(developer)) {
            messages.add(new PromptMessage(PromptRole.DEVELOPER, developer));
        }
        return messages;
    }

    /**
     * 获取模板标识。
     *
     * @return 模板标识
     */
    @Override
    public String getTemplateId() {
        return "default";
    }

    private String buildDeveloperMessage(ContextSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        String resolvedDeveloper = promptTemplateResolver.resolveDeveloperMessage(developerMessage,
                DEFAULT_DEVELOPER_MESSAGE);
        if (StringUtils.hasText(resolvedDeveloper)) {
            builder.append(resolvedDeveloper.trim());
        }
        if (snapshot != null && snapshot.getRoleBoundary() != null
                && StringUtils.hasText(snapshot.getRoleBoundary().getRiskLevel())) {
            builder.append("\n");
            builder.append("风险等级:").append(snapshot.getRoleBoundary().getRiskLevel());
        }
        return builder.toString().trim();
    }
}
