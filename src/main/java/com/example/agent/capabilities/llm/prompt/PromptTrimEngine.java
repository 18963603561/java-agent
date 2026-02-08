package com.example.agent.capabilities.llm.prompt;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextSection;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.capabilities.memory.TokenEstimator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 提示词裁剪引擎。
 *
 * <p>用途：根据预算统一执行 system/developer/user 三段裁剪，并回填预算消耗。
 */
class PromptTrimEngine {

    private static final Logger log = LoggerFactory.getLogger(PromptTrimEngine.class);

    private static final int MIN_SYSTEM_CHARS = 30;
    private static final int MIN_USER_CHARS = 40;
    private static final int MIN_DEVELOPER_CHARS = 10;

    private final TokenEstimator tokenEstimator;
    private final PromptTrimMetricsRecorder metricsRecorder;

    PromptTrimEngine(TokenEstimator tokenEstimator, PromptTrimMetricsRecorder metricsRecorder) {
        this.tokenEstimator = tokenEstimator;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * 根据预算执行裁剪。
     *
     * @param input 装配输入
     * @param trimEnabled 是否启用裁剪
     * @return 裁剪结果
     */
    PromptTrimResult trimIfNeeded(PromptAssemblyInput input, boolean trimEnabled) {
        String systemText = input.getSystemText();
        String developerText = input.getDeveloperText();
        String userText = input.getUserText();

        int systemTokens = estimateTokens(systemText);
        int developerTokens = estimateTokens(developerText);
        int userTokens = estimateTokens(userText);
        int beforeTokens = systemTokens + developerTokens + userTokens;

        ContextBudgetAllocation allocation = input.getBudgetAllocation();
        int budgetTokens = resolvePromptBudgetTokens(allocation);
        if (!trimEnabled || budgetTokens <= 0 || beforeTokens <= budgetTokens) {
            if (!trimEnabled) {
                input.setTruncatedSections(List.of());
            }
            updateBudgetUsed(input, systemText, developerText, userText);
            return new PromptTrimResult(systemText,
                    developerText,
                    userText,
                    input.getTruncatedSections(),
                    beforeTokens,
                    beforeTokens);
        }

        int overTokens = beforeTokens - budgetTokens;
        List<String> truncatedSections = new ArrayList<>(input.getTruncatedSections());

        TrimOutcome developerTrim = trimSection(developerText, overTokens, MIN_DEVELOPER_CHARS);
        if (developerTrim.trimmed()) {
            developerText = developerTrim.text();
            overTokens = Math.max(0, overTokens - developerTrim.tokensReduced());
            addSection(truncatedSections, "developer");
        }

        TrimOutcome userTrim = trimSection(userText, overTokens, MIN_USER_CHARS);
        if (userTrim.trimmed()) {
            userText = userTrim.text();
            overTokens = Math.max(0, overTokens - userTrim.tokensReduced());
            addSection(truncatedSections, "user");
        }

        TrimOutcome systemTrim = trimSection(systemText, overTokens, MIN_SYSTEM_CHARS);
        if (systemTrim.trimmed()) {
            systemText = systemTrim.text();
            addSection(truncatedSections, "system");
        }

        int afterTokens = estimateTokens(systemText) + estimateTokens(developerText) + estimateTokens(userText);
        input.setTruncatedSections(truncatedSections);
        updateBudgetUsed(input, systemText, developerText, userText);
        metricsRecorder.recordTrimMetrics(beforeTokens, afterTokens, truncatedSections);

        log.info("提示词裁剪完成, tenantId={}, workflowId={}, promptBeforeTokens={}, promptAfterTokens={}, truncatedSections={}",
                input.getTenantId(), input.getWorkflowId(), beforeTokens, afterTokens, truncatedSections);
        return new PromptTrimResult(systemText, developerText, userText, truncatedSections, beforeTokens, afterTokens);
    }

    int estimateTokens(List<PromptMessage> messages) {
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

    private int estimateTokens(String text) {
        if (tokenEstimator == null) {
            return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return tokenEstimator.estimateTokens(text);
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 提示词裁剪结果。
     */
    static final class PromptTrimResult {

        private final String systemText;
        private final String developerText;
        private final String userText;
        private final List<String> truncatedSections;
        private final int beforeTokens;
        private final int afterTokens;

        PromptTrimResult(String systemText,
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

        String systemText() {
            return systemText;
        }

        String developerText() {
            return developerText;
        }

        String userText() {
            return userText;
        }

        List<String> truncatedSections() {
            return truncatedSections;
        }

        int beforeTokens() {
            return beforeTokens;
        }

        int afterTokens() {
            return afterTokens;
        }
    }

    /**
     * 段落裁剪结果。
     */
    private record TrimOutcome(String text, int tokensReduced, boolean trimmed) {
    }
}

