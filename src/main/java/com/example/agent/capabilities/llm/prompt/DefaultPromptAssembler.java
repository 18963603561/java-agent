package com.example.agent.capabilities.llm.prompt;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextSection;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.streaming.observability.MetricsPublisher;
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

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultPromptAssembler.class);
    /**
     * 系统提示最小保留长度。
     */
    private static final int MIN_SYSTEM_CHARS = 30;
    /**
     * 用户提示最小保留长度。
     */
    private static final int MIN_USER_CHARS = 40;
    /**
     * 开发者提示最小保留长度。
     */
    private static final int MIN_DEVELOPER_CHARS = 10;

    /**
     * 提示模板。
     */
    private final PromptTemplate promptTemplate;
    /**
     * 令牌估算器。
     */
    private final TokenEstimator tokenEstimator;
    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 参数校验工具。
     */
    private final ValidationSupport validationSupport;

    /**
     * 是否启用提示词裁剪。
     */
    @Value("${agent.prompt.trim.enabled:true}")
    private boolean promptTrimEnabled;

    /**
     * 构造提示组装器。
     *
     * @param promptTemplate 提示模板
     * @param tokenEstimator 令牌估算器
     * @param metricsPublisher 指标发布器
     */
    public DefaultPromptAssembler(PromptTemplate promptTemplate,
                                  TokenEstimator tokenEstimator,
                                  MetricsPublisher metricsPublisher,
                                  ValidationSupport validationSupport) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
        this.validationSupport = validationSupport;
    }

    /**
     * 组装提示消息并按预算进行裁剪。
     *
     * @param prompt 用户提示内容
     * @param taskRequest 任务请求
     * @param stepInput 步骤输入
     * @return 组装结果
     */
    @Override
    public PromptBundle build(String prompt, TaskRequest taskRequest, Map<String, Object> stepInput) {
        String safePrompt = validationSupport != null
                ? validationSupport.normalizeText(prompt, "")
                : (prompt == null ? "" : prompt);
        // 优先解析上下文快照，用于补齐 system/developer
        ContextSnapshot snapshot = resolveSnapshot(taskRequest, stepInput);
        // 优先使用规划阶段生成的装配输入
        PromptAssemblyInput assemblyInput = resolveAssemblyInput(stepInput, taskRequest);
        if (assemblyInput == null) {
            // 兼容旧链路：仅走模板与用户输入拼接
            return buildLegacy(safePrompt, snapshot);
        }
        // 补齐 system/developer/user 等必要字段
        PromptAssemblyInput resolved = enrichAssemblyInput(assemblyInput, snapshot, safePrompt);
        // 按预算执行裁剪，保持行为一致
        PromptTrimResult trimResult = trimIfNeeded(resolved);
        List<PromptMessage> messages = new ArrayList<>();
        // system/developer 为空则不加入，避免噪声
        if (StringUtils.hasText(trimResult.systemText)) {
            messages.add(new PromptMessage(PromptRole.SYSTEM, trimResult.systemText));
        }
        if (StringUtils.hasText(trimResult.developerText)) {
            messages.add(new PromptMessage(PromptRole.DEVELOPER, trimResult.developerText));
        }
        // 用户消息必须存在，空值时使用空字符串占位
        messages.add(new PromptMessage(PromptRole.USER, trimResult.userText == null ? "" : trimResult.userText));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        bundle.setEstimatedTokens(trimResult.afterTokens);
        bundle.setTruncatedSections(trimResult.truncatedSections);

        // 仅统计体量，不输出正文
        int systemChars = length(trimResult.systemText);
        int developerChars = length(trimResult.developerText);
        int userChars = length(trimResult.userText);
        log.debug("提示词组装完成, templateId={}, messageCount={}, systemChars={}, developerChars={}, userChars={}, " +
                        "estimatedTokens={}, trimmed={}",
                bundle.getTemplateId(),
                messages.size(),
                systemChars,
                developerChars,
                userChars,
                trimResult.afterTokens,
                !trimResult.truncatedSections.isEmpty());
        return bundle;
    }

    /**
     * 兼容旧链路的提示组装。
     *
     * @param prompt 用户输入
     * @param snapshot 上下文快照
     * @return 组装结果
     */
    private PromptBundle buildLegacy(String prompt, ContextSnapshot snapshot) {
        List<PromptMessage> messages = new ArrayList<>();
        // legacy 路径仅使用最小渲染上下文
        if (promptTemplate != null) {
            messages.addAll(promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot)));
        }
        // 直接拼接用户输入
        messages.add(new PromptMessage(PromptRole.USER, prompt == null ? "" : prompt));

        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(messages);
        bundle.setTemplateId(promptTemplate != null ? promptTemplate.getTemplateId() : null);
        // 估算总 token，用于上游计量
        int estimatedTokens = estimateTokens(messages);
        bundle.setEstimatedTokens(estimatedTokens);
        bundle.setTruncatedSections(List.of());

        // 仅统计体量，不输出正文
        int systemChars = resolveChars(messages, PromptRole.SYSTEM);
        int developerChars = resolveChars(messages, PromptRole.DEVELOPER);
        int userChars = resolveChars(messages, PromptRole.USER);
        log.debug("提示词组装完成, templateId={}, messageCount={}, systemChars={}, developerChars={}, userChars={}, " +
                        "estimatedTokens={}, trimmed={}",
                bundle.getTemplateId(),
                messages.size(),
                systemChars,
                developerChars,
                userChars,
                estimatedTokens,
                false);
        return bundle;
    }

    /**
     * 解析上下文快照，优先步骤输入。
     *
     * @param taskRequest 任务请求
     * @param stepInput 步骤输入
     * @return 上下文快照
     */
    private ContextSnapshot resolveSnapshot(TaskRequest taskRequest, Map<String, Object> stepInput) {
        // 优先从步骤输入读取，保证本步骤的上下文一致性
        if (stepInput != null) {
            Object snapshot = stepInput.get("contextSnapshot");
            if (snapshot instanceof ContextSnapshot value) {
                return value;
            }
        }
        // 其次从任务请求上下文读取
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
    private PromptAssemblyInput resolveAssemblyInput(Map<String, Object> stepInput, TaskRequest taskRequest) {
        // 优先使用步骤输入中的装配结果
        if (stepInput != null) {
            Object value = stepInput.get("promptAssemblyInput");
            if (value instanceof PromptAssemblyInput input) {
                return input;
            }
        }
        // 其次使用任务请求上下文中的装配结果
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
    private PromptAssemblyInput enrichAssemblyInput(PromptAssemblyInput input,
                                                    ContextSnapshot snapshot,
                                                    String prompt) {
        if (input == null) {
            return null;
        }
        // system/developer 缺失时才补齐，避免覆盖上游配置
        if (!StringUtils.hasText(input.getSystemText()) || !StringUtils.hasText(input.getDeveloperText())) {
            fillSystemDeveloper(input, snapshot);
        }
        // userText 为空时才使用入参兜底
        if (!StringUtils.hasText(input.getUserText())) {
            input.setUserText(prompt);
        }
        // 保证裁剪记录容器不为空
        if (input.getTruncatedSections() == null) {
            input.setTruncatedSections(new ArrayList<>());
        }
        return input;
    }

    /**
     * 回填 system/developer 文本。
     *
     * @param input 装配输入
     * @param snapshot 上下文快照
     */
    private void fillSystemDeveloper(PromptAssemblyInput input, ContextSnapshot snapshot) {
        if (promptTemplate == null || input == null) {
            return;
        }
        // 使用最小渲染上下文，避免运行元数据泄漏
        List<PromptMessage> messages = promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot));
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            // 仅在字段为空时回填，防止覆盖
            if (message.getRole() == PromptRole.SYSTEM && !StringUtils.hasText(input.getSystemText())) {
                input.setSystemText(message.getContent());
            }
            if (message.getRole() == PromptRole.DEVELOPER && !StringUtils.hasText(input.getDeveloperText())) {
                input.setDeveloperText(message.getContent());
            }
        }
    }

    /**
     * 根据预算执行裁剪。
     *
     * @param input 装配输入
     * @return 裁剪结果
     */
    private PromptTrimResult trimIfNeeded(PromptAssemblyInput input) {
        // 取出当前三段提示文本
        String systemText = input.getSystemText();
        String developerText = input.getDeveloperText();
        String userText = input.getUserText();

        // 估算裁剪前 token 用量
        int systemTokens = estimateTokens(systemText);
        int developerTokens = estimateTokens(developerText);
        int userTokens = estimateTokens(userText);
        int beforeTokens = systemTokens + developerTokens + userTokens;

        // 解析预算上限
        ContextBudgetAllocation allocation = input.getBudgetAllocation();
        int budgetTokens = resolvePromptBudgetTokens(allocation);
        if (!promptTrimEnabled || budgetTokens <= 0 || beforeTokens <= budgetTokens) {
            // 未开启裁剪或预算充足，直接回填预算使用
            if (!promptTrimEnabled) {
                input.setTruncatedSections(List.of());
            }
            updateBudgetUsed(input, systemText, developerText, userText);
            return new PromptTrimResult(systemText, developerText, userText,
                    input.getTruncatedSections(), beforeTokens, beforeTokens);
        }

        // 需要裁剪时按顺序回收超额 token
        int overTokens = beforeTokens - budgetTokens;
        List<String> truncatedSections = new ArrayList<>(input.getTruncatedSections());

        // 先裁剪开发者提示，保证系统提示优先保留
        TrimOutcome developerTrim = trimSection(developerText, overTokens, MIN_DEVELOPER_CHARS);
        if (developerTrim.trimmed) {
            developerText = developerTrim.text;
            overTokens = Math.max(0, overTokens - developerTrim.tokensReduced);
            addSection(truncatedSections, "developer");
        }

        // 再裁剪用户输入，尽量保留核心意图
        TrimOutcome userTrim = trimSection(userText, overTokens, MIN_USER_CHARS);
        if (userTrim.trimmed) {
            userText = userTrim.text;
            overTokens = Math.max(0, overTokens - userTrim.tokensReduced);
            addSection(truncatedSections, "user");
        }

        // 最后裁剪系统提示，保留最低长度
        TrimOutcome systemTrim = trimSection(systemText, overTokens, MIN_SYSTEM_CHARS);
        if (systemTrim.trimmed) {
            systemText = systemTrim.text;
            addSection(truncatedSections, "system");
        }

        // 计算裁剪后 token，并更新统计
        int afterTokens = estimateTokens(systemText) + estimateTokens(developerText) + estimateTokens(userText);
        input.setTruncatedSections(truncatedSections);
        updateBudgetUsed(input, systemText, developerText, userText);
        recordTrimMetrics(input, beforeTokens, afterTokens, truncatedSections);

        // 记录裁剪日志，便于追踪预算变化
        log.info("提示词裁剪完成, tenantId={}, workflowId={}, promptBeforeTokens={}, promptAfterTokens={}, truncatedSections={}",
                input.getTenantId(), input.getWorkflowId(), beforeTokens, afterTokens, truncatedSections);
        return new PromptTrimResult(systemText, developerText, userText, truncatedSections, beforeTokens, afterTokens);
    }

    /**
     * 解析提示词预算上限。
     *
     * @param allocation 预算分配
     * @return 预算上限
     */
    private int resolvePromptBudgetTokens(ContextBudgetAllocation allocation) {
        // 预算分配为空时直接返回 0
        if (allocation == null) {
            return 0;
        }
        // 优先取 system/developer/user 三段预算之和
        Map<ContextSection, Integer> sectionTokens = allocation.getSectionTokens();
        int systemBudget = resolveSectionTokens(sectionTokens, ContextSection.SYSTEM_POLICY);
        int developerBudget = resolveSectionTokens(sectionTokens, ContextSection.DEVELOPER_POLICY);
        int userBudget = resolveSectionTokens(sectionTokens, ContextSection.USER_INPUT);
        int sum = systemBudget + developerBudget + userBudget;
        if (sum > 0) {
            return sum;
        }
        // 若未配置分段预算，则回退到总预算
        Integer total = allocation.getTotalTokens();
        return total != null ? Math.max(0, total) : 0;
    }

    private int resolveSectionTokens(Map<ContextSection, Integer> tokens, ContextSection section) {
        // 安全防护：空值直接返回 0
        if (tokens == null || section == null) {
            return 0;
        }
        Integer value = tokens.get(section);
        return value == null ? 0 : Math.max(0, value);
    }

    /**
     * 裁剪单段文本以回收超额 token。
     *
     * @param text 原文本
     * @param overTokens 超额 token
     * @param minChars 最小保留字符
     * @return 裁剪结果
     */
    private TrimOutcome trimSection(String text, int overTokens, int minChars) {
        // 无需裁剪或内容为空时直接返回
        if (!StringUtils.hasText(text) || overTokens <= 0) {
            return new TrimOutcome(text, 0, false);
        }
        // 计算当前 token 与最小保留 token
        int currentTokens = estimateTokens(text);
        int minTokens = estimateTokensByChars(minChars);
        int targetTokens = Math.max(currentTokens - overTokens, minTokens);
        if (targetTokens >= currentTokens) {
            return new TrimOutcome(text, 0, false);
        }
        // 按 token 目标截断尾部
        String trimmed = trimTailByTokens(text, targetTokens, minChars);
        int newTokens = estimateTokens(trimmed);
        int reduced = Math.max(0, currentTokens - newTokens);
        return new TrimOutcome(trimmed, reduced, reduced > 0);
    }

    /**
     * 按目标 token 截断尾部。
     *
     * @param text 原文本
     * @param targetTokens 目标 token
     * @param minChars 最小保留字符
     * @return 截断后的文本
     */
    private String trimTailByTokens(String text, int targetTokens, int minChars) {
        // 空文本直接返回
        if (text == null) {
            return null;
        }
        // token 与字符近似换算
        int maxChars = targetTokens * 4;
        // 保证最小保留字符
        int minKeep = Math.min(text.length(), Math.max(minChars, 0));
        int targetChars = Math.min(text.length(), Math.max(minKeep, maxChars));
        if (targetChars >= text.length()) {
            return text;
        }
        // 从尾部截断，保留前缀
        return text.substring(0, targetChars);
    }

    private int estimateTokensByChars(int chars) {
        // 字符数近似换算为 token 数
        if (chars <= 0) {
            return 0;
        }
        int estimate = (int) Math.ceil(chars / 4.0);
        return Math.max(1, estimate);
    }

    private void addSection(List<String> sections, String section) {
        // 为空时直接返回
        if (sections == null || section == null) {
            return;
        }
        // 避免重复记录
        if (!sections.contains(section)) {
            sections.add(section);
        }
    }

    /**
     * 重新计算并写回预算使用情况。
     *
     * @param input 装配输入
     * @param systemText 系统提示
     * @param developerText 开发者提示
     * @param userText 用户提示
     */
    private void updateBudgetUsed(PromptAssemblyInput input,
                                  String systemText,
                                  String developerText,
                                  String userText) {
        // 重新计算 token 与字符用量
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
        // 写回预算使用情况
        input.setBudgetUsedTokens(tokens);
        input.setBudgetUsedChars(chars);
    }

    private int length(String text) {
        // 空值按 0 处理
        return text == null ? 0 : text.length();
    }

    private int resolveChars(List<PromptMessage> messages, PromptRole role) {
        // 按角色统计字符数
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

    /**
     * 记录裁剪指标，用于观测与告警。
     *
     * @param input 装配输入
     * @param beforeTokens 裁剪前 token
     * @param afterTokens 裁剪后 token
     * @param truncatedSections 被裁剪段落
     */
    private void recordTrimMetrics(PromptAssemblyInput input,
                                   int beforeTokens,
                                   int afterTokens,
                                   List<String> truncatedSections) {
        // 无指标发布器时直接跳过
        if (metricsPublisher == null) {
            return;
        }
        // 记录裁剪统计指标
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
        // 汇总所有消息的 token 估算值
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
        // 未配置估算器时使用字符数近似
        if (tokenEstimator == null) {
            return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return tokenEstimator.estimateTokens(text);
    }

    /**
     * 提示词裁剪结果。
     */
    private static class PromptTrimResult {

        /**
         * 裁剪后的系统提示文本。
         */
        private final String systemText;
        /**
         * 裁剪后的开发者提示文本。
         */
        private final String developerText;
        /**
         * 裁剪后的用户提示文本。
         */
        private final String userText;
        /**
         * 被裁剪的段落标识列表。
         */
        private final List<String> truncatedSections;
        /**
         * 裁剪前 token 数。
         */
        private final int beforeTokens;
        /**
         * 裁剪后 token 数。
         */
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

        /**
         * 裁剪后的文本。
         */
        private final String text;
        /**
         * 本次减少的 token 数。
         */
        private final int tokensReduced;
        /**
         * 是否发生了裁剪。
         */
        private final boolean trimmed;

        private TrimOutcome(String text, int tokensReduced, boolean trimmed) {
            this.text = text;
            this.tokensReduced = tokensReduced;
            this.trimmed = trimmed;
        }
    }
}
