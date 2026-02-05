package com.example.agent.capabilities.llm;

import com.example.agent.capabilities.context.ContextSnapshot;
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
    public static final String DEFAULT_DEVELOPER_MESSAGE = "输出必须结构化且可追溯，遇到不确定先检索再回答。";

    @Value("${agent.prompt.system:" + DEFAULT_SYSTEM_MESSAGE + "}")
    private String systemMessage;

    @Value("${agent.prompt.developer:" + DEFAULT_DEVELOPER_MESSAGE + "}")
    private String developerMessage;

    /**
     * 提示词默认值解析器，用于处理空白配置。
     */
    private final PromptTemplateResolver promptTemplateResolver = new PromptTemplateResolver();

    /**
     * 兼容旧接口：仅映射风险等级后渲染提示词。
     *
     * @param snapshot 上下文快照
     * @return 提示词消息列表
     */
    @Override
    public List<PromptMessage> render(ContextSnapshot snapshot) {
        return render(PromptRenderContext.fromSnapshot(snapshot));
    }

    /**
     * 渲染系统与开发者提示消息。
     *
     * @param context 渲染上下文
     * @return 提示词消息列表
     */
    @Override
    public List<PromptMessage> render(PromptRenderContext context) {
        List<PromptMessage> messages = new ArrayList<>();
        String resolvedSystem = promptTemplateResolver.resolveSystemMessage(systemMessage, DEFAULT_SYSTEM_MESSAGE);
        if (StringUtils.hasText(resolvedSystem)) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, resolvedSystem));
        }
        String developer = buildDeveloperMessage(context);
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

    /**
     * 从快照提取最小渲染上下文，仅保留白名单字段。
     *
     * @param snapshot 上下文快照
     * @return 渲染上下文
     */
    private String buildDeveloperMessage(PromptRenderContext context) {
        StringBuilder builder = new StringBuilder();
        String resolvedDeveloper = promptTemplateResolver.resolveDeveloperMessage(developerMessage,
                DEFAULT_DEVELOPER_MESSAGE);
        if (StringUtils.hasText(resolvedDeveloper)) {
            builder.append(resolvedDeveloper.trim());
        }
        if (context != null && StringUtils.hasText(context.getRiskLevel())) {
            builder.append("\n");
            builder.append("风险等级:").append(context.getRiskLevel());
        }
        return builder.toString().trim();
    }
}
