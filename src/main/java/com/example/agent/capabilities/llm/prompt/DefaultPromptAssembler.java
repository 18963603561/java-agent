package com.example.agent.capabilities.llm.prompt;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.assembly.PromptAssemblyInput;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认提示词组装器。
 *
 * <p>用途：编排提示词输入解析、预算裁剪与消息组装。
 * <p>输入：用户提示、任务请求与步骤输入。
 * <p>输出：可直接用于模型调用的消息包。
 */
@Service
public class DefaultPromptAssembler implements PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptAssembler.class);

    private final PromptTemplate promptTemplate;
    private final ValidationSupport validationSupport;
    private final PromptAssemblyContextResolver contextResolver;
    private final PromptTrimEngine trimEngine;

    @Value("${agent.prompt.trim.enabled:true}")
    private boolean promptTrimEnabled;

    /**
     * 构造提示组装器。
     *
     * @param promptTemplate 提示模板
     * @param tokenEstimator 令牌估算器
     * @param metricsPublisher 指标发布器
     * @param validationSupport 参数校验工具
     */
    public DefaultPromptAssembler(PromptTemplate promptTemplate,
                                  TokenEstimator tokenEstimator,
                                  MetricsPublisher metricsPublisher,
                                  ValidationSupport validationSupport) {
        this.promptTemplate = promptTemplate;
        this.validationSupport = validationSupport;
        this.contextResolver = new PromptAssemblyContextResolver(promptTemplate, metricsPublisher);
        PromptTrimMetricsRecorder metricsRecorder = new PromptTrimMetricsRecorder(metricsPublisher);
        this.trimEngine = new PromptTrimEngine(tokenEstimator, metricsRecorder);
    }

    /**
     * 组装提示消息并按预算进行裁剪。
     *
     * @param prompt 用户提示内容
     * @param taskContext LLM 任务上下文
     * @param stepInput 步骤输入
     * @return 组装结果
     */
    @Override
    public PromptBundle build(String prompt, LlmTaskContext taskContext, Map<String, Object> stepInput) {
        String safePrompt = validationSupport != null
                ? validationSupport.normalizeText(prompt, "")
                : (prompt == null ? "" : prompt);
        ContextSnapshot snapshot = contextResolver.resolveSnapshot(taskContext, stepInput);
        PromptAssemblyInput assemblyInput = contextResolver.resolveAssemblyInput(stepInput, taskContext);
        if (assemblyInput == null) {
            return buildLegacy(safePrompt, snapshot);
        }
        PromptAssemblyInput resolved = contextResolver.enrichAssemblyInput(assemblyInput, snapshot, safePrompt);
        PromptTrimEngine.PromptTrimResult trimResult = trimEngine.trimIfNeeded(resolved, promptTrimEnabled);
        List<PromptMessage> messages = buildMessages(trimResult);

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        bundle.setEstimatedTokens(trimResult.afterTokens());
        bundle.setTruncatedSections(trimResult.truncatedSections());

        int systemChars = length(trimResult.systemText());
        int developerChars = length(trimResult.developerText());
        int userChars = length(trimResult.userText());
        log.debug("提示词组装完成, templateId={}, messageCount={}, systemChars={}, developerChars={}, userChars={}, estimatedTokens={}, trimmed={}",
                bundle.getTemplateId(),
                messages.size(),
                systemChars,
                developerChars,
                userChars,
                trimResult.afterTokens(),
                !trimResult.truncatedSections().isEmpty());
        return bundle;
    }

    private PromptBundle buildLegacy(String prompt, ContextSnapshot snapshot) {
        List<PromptMessage> messages = new ArrayList<>();
        if (promptTemplate != null) {
            messages.addAll(promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot)));
        }
        messages.add(new PromptMessage(PromptRole.USER, prompt == null ? "" : prompt));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        int estimatedTokens = trimEngine.estimateTokens(messages);
        bundle.setEstimatedTokens(estimatedTokens);
        bundle.setTruncatedSections(List.of());

        int systemChars = resolveChars(messages, PromptRole.SYSTEM);
        int developerChars = resolveChars(messages, PromptRole.DEVELOPER);
        int userChars = resolveChars(messages, PromptRole.USER);
        log.debug("提示词组装完成, templateId={}, messageCount={}, systemChars={}, developerChars={}, userChars={}, estimatedTokens={}, trimmed={}",
                bundle.getTemplateId(),
                messages.size(),
                systemChars,
                developerChars,
                userChars,
                estimatedTokens,
                false);
        return bundle;
    }

    private List<PromptMessage> buildMessages(PromptTrimEngine.PromptTrimResult trimResult) {
        List<PromptMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(trimResult.systemText())) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, trimResult.systemText()));
        }
        if (StringUtils.hasText(trimResult.developerText())) {
            messages.add(new PromptMessage(PromptRole.DEVELOPER, trimResult.developerText()));
        }
        messages.add(new PromptMessage(PromptRole.USER, trimResult.userText() == null ? "" : trimResult.userText()));
        return messages;
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private int resolveChars(List<PromptMessage> messages, PromptRole role) {
        if (messages == null || messages.isEmpty() || role == null) {
            return 0;
        }
        int total = 0;
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() != role) {
                continue;
            }
            total += length(message.getContent());
        }
        return total;
    }
}


