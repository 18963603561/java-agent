package com.example.agent.capabilities.context.assembly;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRenderContext;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import com.example.agent.capabilities.llm.prompt.PromptTemplate;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 提示词装配策略应用器。
 *
 * <p>用途：统一 system/developer/user 文本填充策略，避免多处实现漂移。
 */
public class PromptContextPolicyApplier {

    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 构造应用器。
     *
     * @param metricsPublisher 指标发布器
     */
    public PromptContextPolicyApplier(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 按模板策略填充 system/developer。
     *
     * @param input 装配输入
     * @param snapshot 上下文快照
     * @param promptTemplate 提示词模板
     * @param fillOnlyWhenBlank 仅在字段为空时填充
     */
    public void applySystemDeveloper(PromptAssemblyInput input,
                                     ContextSnapshot snapshot,
                                     PromptTemplate promptTemplate,
                                     boolean fillOnlyWhenBlank) {
        if (input == null || promptTemplate == null) {
            return;
        }
        List<PromptMessage> messages = promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot));
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String systemText = null;
        String developerText = null;
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            if (message.getRole() == PromptRole.SYSTEM && !StringUtils.hasText(systemText)) {
                systemText = message.getContent();
            }
            if (message.getRole() == PromptRole.DEVELOPER && !StringUtils.hasText(developerText)) {
                developerText = message.getContent();
            }
        }

        if (shouldFill(fillOnlyWhenBlank, input.getSystemText()) && StringUtils.hasText(systemText)) {
            input.setSystemText(systemText);
            recordBranch("system_template_filled");
        } else {
            recordBranch("system_preserved");
        }
        if (shouldFill(fillOnlyWhenBlank, input.getDeveloperText()) && StringUtils.hasText(developerText)) {
            input.setDeveloperText(developerText);
            recordBranch("developer_template_filled");
        } else {
            recordBranch("developer_preserved");
        }
    }

    /**
     * 按策略填充用户输入。
     *
     * @param input 装配输入
     * @param snapshot 上下文快照
     * @param preferredUserText 显式用户输入
     * @param fillOnlyWhenBlank 仅在字段为空时填充
     */
    public void applyUserText(PromptAssemblyInput input,
                              ContextSnapshot snapshot,
                              String preferredUserText,
                              boolean fillOnlyWhenBlank) {
        if (input == null) {
            return;
        }
        if (!shouldFill(fillOnlyWhenBlank, input.getUserText())) {
            recordBranch("user_preserved");
            return;
        }

        if (StringUtils.hasText(preferredUserText)) {
            input.setUserText(preferredUserText);
            recordBranch("user_from_preferred");
            return;
        }
        if (snapshot != null && snapshot.getTaskIntent() != null
                && StringUtils.hasText(snapshot.getTaskIntent().getInputText())) {
            input.setUserText(snapshot.getTaskIntent().getInputText());
            recordBranch("user_from_snapshot");
            return;
        }
        recordBranch("user_empty");
    }

    /**
     * 判断是否需要填充字段。
     */
    private boolean shouldFill(boolean fillOnlyWhenBlank, String currentValue) {
        if (!fillOnlyWhenBlank) {
            return true;
        }
        return !StringUtils.hasText(currentValue);
    }

    /**
     * 记录策略分支指标。
     */
    private void recordBranch(String branch) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags("context_assemble_policy_branch_total", "branch", branch);
    }
}
