package com.example.agent.budget.trim.estimator;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.capabilities.context.evidence.EvidenceItem;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.model.Citation;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.DomainKnowledge;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.model.ToolCallState;
import com.example.agent.capabilities.context.model.ToolState;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.capabilities.tools.ToolSummary;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文令牌估算服务，提供统一的分段与对象粒度令牌估算能力。
 */
public class ContextTokenEstimator {

    private final ContextTextTokenEstimator textEstimator;
    private final ContextMemoryTokenEstimator memoryEstimator;
    private final ContextKnowledgeTokenEstimator knowledgeEstimator;
    private final ContextToolTokenEstimator toolEstimator;

    public ContextTokenEstimator(TokenEstimator tokenEstimator) {
        this.textEstimator = new ContextTextTokenEstimator(tokenEstimator);
        this.memoryEstimator = new ContextMemoryTokenEstimator(textEstimator);
        this.knowledgeEstimator = new ContextKnowledgeTokenEstimator(textEstimator);
        this.toolEstimator = new ContextToolTokenEstimator(textEstimator);
    }

    /**
     * 估算上下文各分段令牌。
     *
     * @param snapshot 上下文快照
     * @return 分段令牌估算映射
     */
    public Map<ContextSection, Integer> estimateSectionTokens(ContextSnapshot snapshot) {
        EnumMap<ContextSection, Integer> tokens = new EnumMap<>(ContextSection.class);
        if (snapshot == null) {
            return tokens;
        }
        tokens.put(ContextSection.SYSTEM_POLICY, memoryEstimator.estimateRoleBoundarySystemTokens(snapshot.getRoleBoundary()));
        tokens.put(ContextSection.DEVELOPER_POLICY,
                memoryEstimator.estimateRoleBoundaryDeveloperTokens(snapshot.getRoleBoundary()));
        tokens.put(ContextSection.USER_INPUT, memoryEstimator.estimateTaskIntentTokens(snapshot.getTaskIntent()));
        tokens.put(ContextSection.WORKING_MEMORY, memoryEstimator.estimateWorkingMemoryTokens(snapshot.getWorkingMemory()));
        tokens.put(ContextSection.DOMAIN_KNOWLEDGE,
                memoryEstimator.estimateDomainKnowledgeTokens(snapshot.getDomainKnowledge()));
        tokens.put(ContextSection.LONG_TERM_MEMORY,
                memoryEstimator.estimateLongTermMemoryTokens(snapshot.getLongTermMemory()));
        tokens.put(ContextSection.EVIDENCE_PACK, knowledgeEstimator.estimateEvidencePackTokens(snapshot.getWorkingMemory() != null
                ? snapshot.getWorkingMemory().getEvidencePack() : null));
        tokens.put(ContextSection.TOOL_SUMMARY, toolEstimator.estimateToolSummariesTokens(snapshot.getToolState()));
        tokens.put(ContextSection.TOOL_SCHEMA, 0);
        tokens.put(ContextSection.SLACK, 0);
        return tokens;
    }

    /**
     * 估算任务意图令牌。
     *
     * @param intent 任务意图
     * @return 令牌数
     */
    public int estimateTaskIntentTokens(TaskIntent intent) {
        return memoryEstimator.estimateTaskIntentTokens(intent);
    }

    /**
     * 估算系统边界令牌。
     *
     * @param boundary 角色边界
     * @return 令牌数
     */
    public int estimateRoleBoundarySystemTokens(RoleBoundary boundary) {
        return memoryEstimator.estimateRoleBoundarySystemTokens(boundary);
    }

    /**
     * 估算开发者边界令牌。
     *
     * @param boundary 角色边界
     * @return 令牌数
     */
    public int estimateRoleBoundaryDeveloperTokens(RoleBoundary boundary) {
        return memoryEstimator.estimateRoleBoundaryDeveloperTokens(boundary);
    }

    /**
     * 估算工作记忆令牌。
     *
     * @param memory 工作记忆
     * @return 令牌数
     */
    public int estimateWorkingMemoryTokens(WorkingMemory memory) {
        return memoryEstimator.estimateWorkingMemoryTokens(memory);
    }

    /**
     * 估算领域知识令牌。
     *
     * @param knowledge 领域知识
     * @return 令牌数
     */
    public int estimateDomainKnowledgeTokens(DomainKnowledge knowledge) {
        return memoryEstimator.estimateDomainKnowledgeTokens(knowledge);
    }

    /**
     * 估算引用列表令牌。
     *
     * @param citations 引用列表
     * @return 令牌数
     */
    public int estimateDomainKnowledgeTokens(List<Citation> citations) {
        return memoryEstimator.estimateDomainKnowledgeTokens(citations);
    }

    /**
     * 估算长期记忆令牌。
     *
     * @param memory 长期记忆
     * @return 令牌数
     */
    public int estimateLongTermMemoryTokens(LongTermMemory memory) {
        return memoryEstimator.estimateLongTermMemoryTokens(memory);
    }

    /**
     * 估算记忆引用列表令牌。
     *
     * @param refs 记忆引用列表
     * @return 令牌数
     */
    public int estimateLongTermMemoryTokens(List<MemoryRef> refs) {
        return memoryEstimator.estimateLongTermMemoryTokens(refs);
    }

    /**
     * 估算证据包令牌。
     *
     * @param pack 证据包
     * @return 令牌数
     */
    public int estimateEvidencePackTokens(EvidencePack pack) {
        return knowledgeEstimator.estimateEvidencePackTokens(pack);
    }

    /**
     * 估算工具状态令牌。
     *
     * @param toolState 工具状态
     * @return 令牌数
     */
    public int estimateToolSummariesTokens(ToolState toolState) {
        return toolEstimator.estimateToolSummariesTokens(toolState);
    }

    /**
     * 估算工具摘要列表令牌。
     *
     * @param tools 工具摘要列表
     * @return 令牌数
     */
    public int estimateToolSummariesTokens(List<ToolSummary> tools) {
        return toolEstimator.estimateToolSummariesTokens(tools);
    }

    /**
     * 估算文本令牌。
     *
     * @param text 文本
     * @return 令牌数
     */
    public int estimateTokens(String text) {
        return textEstimator.estimateTokens(text);
    }

    /**
     * 估算文本列表令牌。
     *
     * @param values 文本列表
     * @return 令牌数
     */
    public int estimateTokens(List<String> values) {
        return textEstimator.estimateTokens(values);
    }

    /**
     * 基于字符数估算令牌。
     *
     * @param chars 字符数
     * @return 令牌数
     */
    public int estimateTokensByChars(int chars) {
        return textEstimator.estimateTokensByChars(chars);
    }

    /**
     * 估算记忆引用令牌。
     *
     * @param ref 记忆引用
     * @return 令牌数
     */
    public int estimateMemoryRefTokens(MemoryRef ref) {
        return memoryEstimator.estimateMemoryRefTokens(ref);
    }

    /**
     * 估算记忆引用字符数。
     *
     * @param ref 记忆引用
     * @return 字符数
     */
    public int estimateMemoryRefChars(MemoryRef ref) {
        return memoryEstimator.estimateMemoryRefChars(ref);
    }

    /**
     * 估算引用令牌。
     *
     * @param citation 引用对象
     * @return 令牌数
     */
    public int estimateCitationTokens(Citation citation) {
        return memoryEstimator.estimateCitationTokens(citation);
    }

    /**
     * 估算引用字符数。
     *
     * @param citation 引用对象
     * @return 字符数
     */
    public int estimateCitationChars(Citation citation) {
        return memoryEstimator.estimateCitationChars(citation);
    }

    /**
     * 估算工具摘要令牌。
     *
     * @param tool 工具摘要
     * @return 令牌数
     */
    public int estimateToolSummaryTokens(ToolSummary tool) {
        return toolEstimator.estimateToolSummaryTokens(tool);
    }

    /**
     * 估算工具摘要字符数。
     *
     * @param tool 工具摘要
     * @return 字符数
     */
    public int estimateToolSummaryChars(ToolSummary tool) {
        return toolEstimator.estimateToolSummaryChars(tool);
    }

    /**
     * 估算证据项令牌。
     *
     * @param item 证据项
     * @return 令牌数
     */
    public int estimateEvidenceItemTokens(EvidenceItem item) {
        return knowledgeEstimator.estimateEvidenceItemTokens(item);
    }

    /**
     * 估算证据项字符数。
     *
     * @param item 证据项
     * @return 字符数
     */
    public int estimateEvidenceItemChars(EvidenceItem item) {
        return knowledgeEstimator.estimateEvidenceItemChars(item);
    }

    /**
     * 估算工具调用状态列表令牌。
     *
     * @param calls 调用状态列表
     * @return 令牌数
     */
    public int estimateToolCallStateTokens(List<ToolCallState> calls) {
        return memoryEstimator.estimateToolCallStateTokens(calls);
    }

    /**
     * 估算工具调用状态令牌。
     *
     * @param call 调用状态
     * @return 令牌数
     */
    public int estimateToolCallStateTokens(ToolCallState call) {
        return memoryEstimator.estimateToolCallStateTokens(call);
    }

    /**
     * 估算工具调用状态字符数。
     *
     * @param call 调用状态
     * @return 字符数
     */
    public int estimateToolCallStateChars(ToolCallState call) {
        return memoryEstimator.estimateToolCallStateChars(call);
    }

    /**
     * 汇总各分段令牌。
     *
     * @param tokens 分段令牌映射
     * @return 总令牌数
     */
    public int sumTokens(Map<ContextSection, Integer> tokens) {
        int total = 0;
        if (tokens == null) {
            return total;
        }
        for (Integer value : tokens.values()) {
            total += value == null ? 0 : value;
        }
        return total;
    }

    /**
     * 计算字符串长度。
     *
     * @param value 字符串
     * @return 长度
     */
    public int safeLength(String value) {
        return textEstimator.safeLength(value);
    }
}
