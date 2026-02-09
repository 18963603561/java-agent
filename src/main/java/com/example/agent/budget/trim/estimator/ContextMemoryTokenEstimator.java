package com.example.agent.budget.trim.estimator;

import com.example.agent.capabilities.context.model.Citation;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.model.ToolCallState;
import com.example.agent.capabilities.context.model.WorkingMemory;
import java.util.List;

/**
 * 记忆与任务相关令牌估算器。
 */
class ContextMemoryTokenEstimator {

    private final ContextTextTokenEstimator textEstimator;

    ContextMemoryTokenEstimator(ContextTextTokenEstimator textEstimator) {
        this.textEstimator = textEstimator;
    }

    /**
     * 估算任务意图令牌。
     */
    int estimateTaskIntentTokens(TaskIntent intent) {
        if (intent == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(intent.getInputText());
        total += textEstimator.estimateTokens(intent.getSuccessCriteria());
        total += textEstimator.estimateTokens(intent.getFailurePolicy());
        total += textEstimator.estimateTokens(intent.getRequiredOutput());
        total += textEstimator.estimateTokens(intent.getConstraints());
        return total;
    }

    /**
     * 估算系统边界令牌。
     */
    int estimateRoleBoundarySystemTokens(RoleBoundary boundary) {
        if (boundary == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(boundary.getSystemPolicyId());
        total += textEstimator.estimateTokens(boundary.getRiskLevel());
        total += textEstimator.estimateTokens(boundary.getForbiddenActions());
        total += textEstimator.estimateTokens(boundary.getDataScopes());
        return total;
    }

    /**
     * 估算开发者边界令牌。
     */
    int estimateRoleBoundaryDeveloperTokens(RoleBoundary boundary) {
        if (boundary == null) {
            return 0;
        }
        return textEstimator.estimateTokens(boundary.getDeveloperPolicyId());
    }

    /**
     * 估算工作记忆令牌。
     */
    int estimateWorkingMemoryTokens(WorkingMemory memory) {
        if (memory == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(memory.getSummary());
        total += textEstimator.estimateTokens(memory.getKeyFacts());
        total += textEstimator.estimateTokens(memory.getPlanSteps());
        total += textEstimator.estimateTokens(memory.getNextStep());
        total += estimateToolCallStateTokens(memory.getRecentToolCalls());
        return total;
    }

    /**
     * 估算领域知识令牌。
     */
    int estimateDomainKnowledgeTokens(List<Citation> citations) {
        if (citations == null) {
            return 0;
        }
        int total = 0;
        for (Citation citation : citations) {
            total += estimateCitationTokens(citation);
        }
        return total;
    }

    /**
     * 估算长期记忆令牌。
     */
    int estimateLongTermMemoryTokens(List<MemoryRef> refs) {
        if (refs == null) {
            return 0;
        }
        int total = 0;
        for (MemoryRef ref : refs) {
            total += estimateMemoryRefTokens(ref);
        }
        return total;
    }

    /**
     * 估算记忆引用令牌。
     */
    int estimateMemoryRefTokens(MemoryRef ref) {
        if (ref == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(ref.getMemoryId());
        total += textEstimator.estimateTokens(ref.getMemoryType());
        total += textEstimator.estimateTokens(ref.getSnippet());
        total += textEstimator.estimateTokens(ref.getSource());
        return total;
    }

    /**
     * 估算记忆引用字符数。
     */
    int estimateMemoryRefChars(MemoryRef ref) {
        if (ref == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.safeLength(ref.getMemoryId());
        total += textEstimator.safeLength(ref.getMemoryType());
        total += textEstimator.safeLength(ref.getSnippet());
        total += textEstimator.safeLength(ref.getSource());
        return total;
    }

    /**
     * 估算引用令牌。
     */
    int estimateCitationTokens(Citation citation) {
        if (citation == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(citation.getType());
        total += textEstimator.estimateTokens(citation.getRefId());
        total += textEstimator.estimateTokens(citation.getLabel());
        total += textEstimator.estimateTokens(citation.getSource());
        total += textEstimator.estimateTokens(citation.getTitle());
        total += textEstimator.estimateTokens(citation.getUri());
        total += textEstimator.estimateTokens(citation.getSnippet());
        return total;
    }

    /**
     * 估算引用字符数。
     */
    int estimateCitationChars(Citation citation) {
        if (citation == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.safeLength(citation.getType());
        total += textEstimator.safeLength(citation.getRefId());
        total += textEstimator.safeLength(citation.getLabel());
        total += textEstimator.safeLength(citation.getSource());
        total += textEstimator.safeLength(citation.getTitle());
        total += textEstimator.safeLength(citation.getUri());
        total += textEstimator.safeLength(citation.getSnippet());
        return total;
    }

    /**
     * 估算工具调用状态列表令牌。
     */
    int estimateToolCallStateTokens(List<ToolCallState> calls) {
        if (calls == null) {
            return 0;
        }
        int total = 0;
        for (ToolCallState call : calls) {
            total += estimateToolCallStateTokens(call);
        }
        return total;
    }

    /**
     * 估算工具调用状态令牌。
     */
    int estimateToolCallStateTokens(ToolCallState call) {
        if (call == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.estimateTokens(call.getToolName());
        total += textEstimator.estimateTokens(call.getRequestId());
        total += textEstimator.estimateTokens(call.getErrorCode());
        return total;
    }

    /**
     * 估算工具调用状态字符数。
     */
    int estimateToolCallStateChars(ToolCallState call) {
        if (call == null) {
            return 0;
        }
        int total = 0;
        total += textEstimator.safeLength(call.getToolName());
        total += textEstimator.safeLength(call.getRequestId());
        total += textEstimator.safeLength(call.getErrorCode());
        return total;
    }

    /**
     * 估算领域知识令牌。
     */
    int estimateDomainKnowledgeTokens(com.example.agent.capabilities.context.model.DomainKnowledge knowledge) {
        if (knowledge == null) {
            return 0;
        }
        return estimateDomainKnowledgeTokens(knowledge.getCitations());
    }

    /**
     * 估算长期记忆令牌。
     */
    int estimateLongTermMemoryTokens(LongTermMemory memory) {
        if (memory == null) {
            return 0;
        }
        return estimateLongTermMemoryTokens(memory.getMemoryRefs());
    }
}
