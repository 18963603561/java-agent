package com.example.agent.model;

import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextSection;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.PromptAssemblyInput;
import com.example.agent.memory.TokenEstimator;
import com.example.agent.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认提示词组装器。
 */
@Service
public class DefaultPromptAssembler implements PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptAssembler.class);
    private static final int MIN_SYSTEM_CHARS = 30;
    private static final int MIN_USER_CHARS = 40;
    private static final int MIN_DEVELOPER_CHARS = 10;

    private final PromptTemplate promptTemplate;
    private final TokenEstimator tokenEstimator;
    private final MetricsPublisher metricsPublisher;

    /**
     * 是否启用提示词裁剪。
     */
    @Value("${agent.prompt.trim.enabled:true}")
    private boolean promptTrimEnabled;

    public DefaultPromptAssembler(PromptTemplate promptTemplate,
                                  TokenEstimator tokenEstimator,
                                  MetricsPublisher metricsPublisher) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public PromptBundle build(String prompt, TaskRequest taskRequest, Map<String, Object> stepInput) {
        ContextSnapshot snapshot = resolveSnapshot(taskRequest, stepInput);
        PromptAssemblyInput assemblyInput = resolveAssemblyInput(stepInput, taskRequest);
        if (assemblyInput == null) {
            return buildLegacy(prompt, snapshot);
        }
        PromptAssemblyInput resolved = enrichAssemblyInput(assemblyInput, snapshot, prompt);
        PromptTrimResult trimResult = trimIfNeeded(resolved);
        List<PromptMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(trimResult.systemText)) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, trimResult.systemText));
        }
        if (StringUtils.hasText(trimResult.developerText)) {
            messages.add(new PromptMessage(PromptRole.DEVELOPER, trimResult.developerText));
        }
        messages.add(new PromptMessage(PromptRole.USER, trimResult.userText == null ? "" : trimResult.userText));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        bundle.setEstimatedTokens(trimResult.afterTokens);
        bundle.setTruncatedSections(trimResult.truncatedSections);

        log.debug("提示词组装完成, templateId={}, messageCount={}, trimmed={}",
                bundle.getTemplateId(), messages.size(), !trimResult.truncatedSections.isEmpty());
        return bundle;
    }

    private PromptBundle buildLegacy(String prompt, ContextSnapshot snapshot) {
        List<PromptMessage> messages = new ArrayList<>();
        if (promptTemplate != null) {
            messages.addAll(promptTemplate.render(snapshot));
        }
        messages.add(new PromptMessage(PromptRole.USER, prompt == null ? "" : prompt));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        bundle.setEstimatedTokens(estimateTokens(messages));
        bundle.setTruncatedSections(List.of());

        log.debug("提示词组装完成, templateId={}, messageCount={}",
                bundle.getTemplateId(), messages.size());
        return bundle;
    }

    private ContextSnapshot resolveSnapshot(TaskRequest taskRequest, Map<String, Object> stepInput) {
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

    private PromptAssemblyInput resolveAssemblyInput(Map<String, Object> stepInput, TaskRequest taskRequest) {
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

    private PromptAssemblyInput enrichAssemblyInput(PromptAssemblyInput input,
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
        List<PromptMessage> messages = promptTemplate.render(snapshot);
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

    private PromptTrimResult trimIfNeeded(PromptAssemblyInput input) {
        String systemText = input.getSystemText();
        String developerText = input.getDeveloperText();
        String userText = input.getUserText();

        int systemTokens = estimateTokens(systemText);
        int developerTokens = estimateTokens(developerText);
        int userTokens = estimateTokens(userText);
        int beforeTokens = systemTokens + developerTokens + userTokens;

        ContextBudgetAllocation allocation = input.getBudgetAllocation();
        int budgetTokens = resolvePromptBudgetTokens(allocation);
        if (!promptTrimEnabled || budgetTokens <= 0 || beforeTokens <= budgetTokens) {
            if (!promptTrimEnabled) {
                input.setTruncatedSections(List.of());
            }
            updateBudgetUsed(input, systemText, developerText, userText);
            return new PromptTrimResult(systemText, developerText, userText,
                    input.getTruncatedSections(), beforeTokens, beforeTokens);
        }

        int overTokens = beforeTokens - budgetTokens;
        List<String> truncatedSections = new ArrayList<>(input.getTruncatedSections());

        TrimOutcome developerTrim = trimSection(developerText, overTokens, MIN_DEVELOPER_CHARS);
        if (developerTrim.trimmed) {
            developerText = developerTrim.text;
            overTokens = Math.max(0, overTokens - developerTrim.tokensReduced);
            addSection(truncatedSections, "developer");
        }

        TrimOutcome userTrim = trimSection(userText, overTokens, MIN_USER_CHARS);
        if (userTrim.trimmed) {
            userText = userTrim.text;
            overTokens = Math.max(0, overTokens - userTrim.tokensReduced);
            addSection(truncatedSections, "user");
        }

        TrimOutcome systemTrim = trimSection(systemText, overTokens, MIN_SYSTEM_CHARS);
        if (systemTrim.trimmed) {
            systemText = systemTrim.text;
            addSection(truncatedSections, "system");
        }

        int afterTokens = estimateTokens(systemText) + estimateTokens(developerText) + estimateTokens(userText);
        input.setTruncatedSections(truncatedSections);
        updateBudgetUsed(input, systemText, developerText, userText);
        recordTrimMetrics(input, beforeTokens, afterTokens, truncatedSections);

        log.info("提示词裁剪完成, tenantId={}, workflowId={}, promptBeforeTokens={}, promptAfterTokens={}, truncatedSections={}",
                input.getTenantId(), input.getWorkflowId(), beforeTokens, afterTokens, truncatedSections);
        return new PromptTrimResult(systemText, developerText, userText, truncatedSections, beforeTokens, afterTokens);
    }

    private int resolvePromptBudgetTokens(ContextBudgetAllocation allocation) {
        if (allocation == null) {
            return 0;
        }
        Map<ContextSection, Integer> sectionTokens = allocation.getSectionTokens();
        int systemBudget = resolveSectionTokens(sectionTokens, ContextSection.SYSTEM_POLICY);
        int developerBudget = resolveSectionTokens(sectionTokens, ContextSection.DEVELOPER_POLICY);
        int userBudget = resolveSectionTokens(sectionTokens, ContextSection.USER_INPUT);
        int sum = systemBudget + developerBudget + userBudget;
        if (sum > 0) {
            return sum;
        }
        Integer total = allocation.getTotalTokens();
        return total != null ? Math.max(0, total) : 0;
    }

    private int resolveSectionTokens(Map<ContextSection, Integer> tokens, ContextSection section) {
        if (tokens == null || section == null) {
            return 0;
        }
        Integer value = tokens.get(section);
        return value == null ? 0 : Math.max(0, value);
    }

    private TrimOutcome trimSection(String text, int overTokens, int minChars) {
        if (!StringUtils.hasText(text) || overTokens <= 0) {
            return new TrimOutcome(text, 0, false);
        }
        int currentTokens = estimateTokens(text);
        int minTokens = estimateTokensByChars(minChars);
        int targetTokens = Math.max(currentTokens - overTokens, minTokens);
        if (targetTokens >= currentTokens) {
            return new TrimOutcome(text, 0, false);
        }
        String trimmed = trimTailByTokens(text, targetTokens, minChars);
        int newTokens = estimateTokens(trimmed);
        int reduced = Math.max(0, currentTokens - newTokens);
        return new TrimOutcome(trimmed, reduced, reduced > 0);
    }

    private String trimTailByTokens(String text, int targetTokens, int minChars) {
        if (text == null) {
            return null;
        }
        int maxChars = targetTokens * 4;
        int minKeep = Math.min(text.length(), Math.max(minChars, 0));
        int targetChars = Math.min(text.length(), Math.max(minKeep, maxChars));
        if (targetChars >= text.length()) {
            return text;
        }
        return text.substring(0, targetChars);
    }

    private int estimateTokensByChars(int chars) {
        if (chars <= 0) {
            return 0;
        }
        int estimate = (int) Math.ceil(chars / 4.0);
        return Math.max(1, estimate);
    }

    private void addSection(List<String> sections, String section) {
        if (sections == null || section == null) {
            return;
        }
        if (!sections.contains(section)) {
            sections.add(section);
        }
    }

    private void updateBudgetUsed(PromptAssemblyInput input,
                                  String systemText,
                                  String developerText,
                                  String userText) {
        Map<String, Integer> tokens = new HashMap<>();
        Map<String, Integer> chars = new HashMap<>();
        int systemTokens = estimateTokens(systemText);
        int developerTokens = estimateTokens(developerText);
        int userTokens = estimateTokens(userText);
        int totalTokens = systemTokens + developerTokens + userTokens;
        tokens.put("system", systemTokens);
        tokens.put("developer", developerTokens);
        tokens.put("user", userTokens);
        tokens.put("total", totalTokens);
        chars.put("system", length(systemText));
        chars.put("developer", length(developerText));
        chars.put("user", length(userText));
        chars.put("total", length(systemText) + length(developerText) + length(userText));
        input.setBudgetUsedTokens(tokens);
        input.setBudgetUsedChars(chars);
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private void recordTrimMetrics(PromptAssemblyInput input,
                                   int beforeTokens,
                                   int afterTokens,
                                   List<String> truncatedSections) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.increment("prompt_trim_runs_total");
        metricsPublisher.recordSummary("prompt_trim_before_tokens", beforeTokens);
        metricsPublisher.recordSummary("prompt_trim_after_tokens", afterTokens);
        if (truncatedSections != null) {
            for (String section : truncatedSections) {
                if (section == null || section.isBlank()) {
                    continue;
                }
                metricsPublisher.incrementWithTags("prompt_truncated_sections_total", "section", section);
            }
        }
    }

    private int estimateTokens(List<PromptMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (PromptMessage message : messages) {
            if (message == null) {
                continue;
            }
            total += estimateTokens(message.getContent());
        }
        return total;
    }

    private int estimateTokens(String text) {
        if (tokenEstimator == null) {
            return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return tokenEstimator.estimateTokens(text);
    }

    /**
     * 提示词裁剪结果。
     */
    private static class PromptTrimResult {

        private final String systemText;
        private final String developerText;
        private final String userText;
        private final List<String> truncatedSections;
        private final int beforeTokens;
        private final int afterTokens;

        private PromptTrimResult(String systemText,
                                 String developerText,
                                 String userText,
                                 List<String> truncatedSections,
                                 int beforeTokens,
                                 int afterTokens) {
            this.systemText = systemText;
            this.developerText = developerText;
            this.userText = userText;
            this.truncatedSections = truncatedSections == null ? List.of() : truncatedSections;
            this.beforeTokens = beforeTokens;
            this.afterTokens = afterTokens;
        }
    }

    /**
     * 段落裁剪结果。
     */
    private static class TrimOutcome {

        private final String text;
        private final int tokensReduced;
        private final boolean trimmed;

        private TrimOutcome(String text, int tokensReduced, boolean trimmed) {
            this.text = text;
            this.tokensReduced = tokensReduced;
            this.trimmed = trimmed;
        }
    }
}
