package com.example.agent.capabilities.context.assembly;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.llm.prompt.PromptTemplate;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
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
     * 装配策略应用器。
     */
    private final PromptContextPolicyApplier policyApplier;

    /**
     * 构造默认装配器。
     *
     * @param promptTemplate 提示词模板
     * @param tokenEstimator 令牌估算器
     */
    public DefaultContextAssembler(PromptTemplate promptTemplate,
                                   TokenEstimator tokenEstimator,
                                   MetricsPublisher metricsPublisher) {
        this.promptTemplate = promptTemplate;
        this.tokenEstimator = tokenEstimator;
        this.policyApplier = new PromptContextPolicyApplier(metricsPublisher);
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
    public PromptAssemblyInput assemble(ContextAssemblyCommand command) {
        ContextSnapshot snapshot = command != null ? command.getSnapshot() : null;
        ContextBudgetAllocation allocation = command != null ? command.getAllocation() : ContextBudgetAllocation.EMPTY;
        ContextTrimReport trimReport = command != null ? command.getTrimReport() : null;
        ContextPruneResult pruneResult = command != null ? command.getPruneResult() : null;
        ContextCompressionResult compressionResult = command != null ? command.getCompressionResult() : null;
        String tenantId = command != null ? command.getTenantId() : null;
        String workflowId = command != null ? command.getWorkflowId() : null;
        String userText = command != null ? command.getUserText() : null;

        PromptAssemblyInput input = new PromptAssemblyInput();
        input.setTenantId(tenantId);
        input.setWorkflowId(workflowId);
        input.setBudgetAllocation(allocation);
        if (allocation.isAllocationEnabled() && StringUtils.hasText(allocation.getVersion())) {
            input.setPolicyVersion(allocation.getVersion());
        }
        policyApplier.applySystemDeveloper(input, snapshot, promptTemplate, false);
        policyApplier.applyUserText(input, snapshot, userText, false);
        input.setTruncatedSections(buildTruncatedSections(trimReport, pruneResult, compressionResult));

        Map<String, Object> assemblyMeta = new HashMap<>();
        assemblyMeta.put("trimmed", trimReport != null);
        assemblyMeta.put("pruned", pruneResult != null);
        assemblyMeta.put("compressed", compressionResult != null && compressionResult.isTriggered());
        if (trimReport != null) {
            assemblyMeta.put("trimReasons", trimReport.getReasons());
            assemblyMeta.put("trimBeforeTokens", trimReport.getTotalBeforeTokens());
            assemblyMeta.put("trimAfterTokens", trimReport.getTotalAfterTokens());
        }
        if (pruneResult != null) {
            assemblyMeta.put("pruneSummary", pruneResult.getSummary());
            assemblyMeta.put("prunedCount", pruneResult.getRemovedItems() != null ? pruneResult.getRemovedItems().size() : 0);
        }
        if (compressionResult != null) {
            assemblyMeta.put("compressionTriggered", compressionResult.isTriggered());
            assemblyMeta.put("compressionReason", compressionResult.getTriggerReason());
            assemblyMeta.put("compressionBeforeTokens", compressionResult.getBeforeTokens());
            assemblyMeta.put("compressionAfterTokens", compressionResult.getAfterCompressTokens());
        }
        input.setAssemblyMetadata(assemblyMeta);

        fillBudgetUsage(input);
        return input;
    }

    /**
     * 构建裁剪段落摘要。
     */
    private List<String> buildTruncatedSections(ContextTrimReport trimReport,
                                                ContextPruneResult pruneResult,
                                                ContextCompressionResult compressionResult) {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        if (trimReport != null) {
            sections.add("context_trimmed");
        }
        if (pruneResult != null) {
            sections.add("context_pruned");
        }
        if (compressionResult != null && compressionResult.isTriggered()) {
            sections.add("context_compressed");
        }
        return new ArrayList<>(sections);
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



