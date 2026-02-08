package com.example.agent.capabilities.context;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextCompressionResult;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.budget.trim.ContextTrimReport;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRenderContext;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import com.example.agent.capabilities.llm.prompt.PromptTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认上下文装配器，用于生成提示词装配输入。
 */
@Service
public class DefaultContextAssembler implements ContextAssembler {

    /**
     * 提示词模板渲染器，仅用于生成 system 与 developer 文本。
     */
    private final PromptTemplate promptTemplate;
    /**
     * 令牌估算器，用于预算统计与可观测指标。
     */
    private final TokenEstimator tokenEstimator;

    /**
     * 构造默认装配器。
     *
     * @param promptTemplate 提示词模板
     * @param tokenEstimator 令牌估算器
     */
    public DefaultContextAssembler(PromptTemplate promptTemplate, TokenEstimator tokenEstimator) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 装配提示词输入。
     * <p>主要步骤：写入租户与工作流、应用预算、生成 system/developer、补齐用户输入、计算预算占用。</p>
     *
     * @param snapshot 上下文快照
     * @param allocation 预算分配
     * @param trimReport 裁剪报告（当前用于透传）
     * @param pruneResult 剪枝结果（当前用于透传）
     * @param compressionResult 压缩结果（当前用于透传）
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param userText 用户输入
     * @return 装配后的输入
     */
    @Override
    public PromptAssemblyInput assemble(ContextSnapshot snapshot,
                                        ContextBudgetAllocation allocation,
                                        ContextTrimReport trimReport,
                                        ContextPruneResult pruneResult,
                                        ContextCompressionResult compressionResult,
                                        String tenantId,
                                        String workflowId,
                                        String userText) {
        // 裁剪、剪枝与压缩结果当前仅透传保存
        // 若后续需要映射到提示词输入，需在此补齐规则
        // 该设计用于隔离运行层字段进入提示词
        // 初始化装配结果并写入基础标识
        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setTenantId(tenantId);
        input.setWorkflowId(workflowId);
        // 预算分配用于后续裁剪与统计
        input.setBudgetAllocation(allocation);
        if (allocation != null && StringUtils.hasText(allocation.getVersion())) {
            // 记录预算策略版本，便于审计与回放
            input.setPolicyVersion(allocation.getVersion());
        }
        // 仅使用最小上下文生成 system/developer
        fillSystemDeveloper(input, snapshot);
        // 用户输入优先使用显式文本，其次用快照兜底
        input.setUserText(resolveUserText(snapshot, userText));
        // 初始化裁剪记录容器，后续可追加
        // 裁剪记录用于提示词缩减追踪
        input.setTruncatedSections(new ArrayList<>());
        // 计算本次提示词的预算占用
        fillBudgetUsage(input);
        return input;
    }

    private void fillSystemDeveloper(PromptAssemblyInput input, ContextSnapshot snapshot) {
        if (promptTemplate == null) {
            return;
        }
        // 使用白名单渲染上下文，避免运行元数据泄漏
        List<PromptMessage> messages = promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot));
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // 仅处理 system/developer 角色
        // 其他角色忽略，避免污染装配输入
        for (PromptMessage message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            // 按角色写入 system/developer 文本
            if (message.getRole() == PromptRole.SYSTEM) {
                input.setSystemText(message.getContent());
            } else if (message.getRole() == PromptRole.DEVELOPER) {
                input.setDeveloperText(message.getContent());
            }
        }
    }

    private String resolveUserText(ContextSnapshot snapshot, String userText) {
        // 明确传入的用户文本优先级最高
        if (StringUtils.hasText(userText)) {
            return userText;
        }
        // 允许使用快照中的可读输入作为兜底
        if (snapshot != null && snapshot.getTaskIntent() != null
                && StringUtils.hasText(snapshot.getTaskIntent().getInputText())) {
            return snapshot.getTaskIntent().getInputText();
        }
        return null;
    }

    private void fillBudgetUsage(PromptAssemblyInput input) {
        // 估算 token 使用量，便于预算与观测
        Map<String, Integer> tokens = new HashMap<>();
        // 统计字符数，便于追踪剪裁效果
        Map<String, Integer> chars = new HashMap<>();
        int systemTokens = estimateTokens(input.getSystemText());
        int developerTokens = estimateTokens(input.getDeveloperText());
        int userTokens = estimateTokens(input.getUserText());
        int totalTokens = systemTokens + developerTokens + userTokens;
        tokens.put("system", systemTokens);
        tokens.put("developer", developerTokens);
        tokens.put("user", userTokens);
        tokens.put("total", totalTokens);

        int systemChars = length(input.getSystemText());
        int developerChars = length(input.getDeveloperText());
        int userChars = length(input.getUserText());
        int totalChars = systemChars + developerChars + userChars;
        chars.put("system", systemChars);
        chars.put("developer", developerChars);
        chars.put("user", userChars);
        chars.put("total", totalChars);

        // 写回预算占用信息
        input.setBudgetUsedTokens(tokens);
        input.setBudgetUsedChars(chars);
    }

    private int estimateTokens(String text) {
        // 未配置估算器时使用字符数近似
        if (tokenEstimator == null) {
            return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
        }
        return tokenEstimator.estimateTokens(text);
    }

    private int length(String text) {
        // 统一处理空值
        return text == null ? 0 : text.length();
    }
}

