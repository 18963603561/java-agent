package com.example.agent.budget.trim.handler;

import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.capabilities.context.model.Citation;
import com.example.agent.capabilities.context.model.DomainKnowledge;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 召回记忆分组裁剪处理器。
 */
public class RecalledMemoriesTrimHandler extends AbstractContextTrimHandler {

    @Override
    public ContextTrimSection section() {
        return ContextTrimSection.RECALLED_MEMORIES;
    }

    @Override
    public void trimBySectionBudget(TrimContext context) {
        Integer longBudget = resolveBudget(context.getBudgets(), ContextSection.LONG_TERM_MEMORY);
        int longTokens = context.getCurrentTokens().getOrDefault(ContextSection.LONG_TERM_MEMORY, 0);
        if (longBudget != null && longTokens > longBudget) {
            trimLongTermMemory(context, longBudget);
            context.refreshTokens();
        }
        Integer domainBudget = resolveBudget(context.getBudgets(), ContextSection.DOMAIN_KNOWLEDGE);
        int domainTokens = context.getCurrentTokens().getOrDefault(ContextSection.DOMAIN_KNOWLEDGE, 0);
        if (domainBudget != null && domainTokens > domainBudget) {
            trimDomainKnowledge(context, domainBudget);
            context.refreshTokens();
        }
    }

    @Override
    public int trimByTotalBudget(TrimContext context, int excess) {
        if (excess <= 0) {
            return 0;
        }
        int longTokens = context.getCurrentTokens().getOrDefault(ContextSection.LONG_TERM_MEMORY, 0);
        if (longTokens > 0 && excess > 0) {
            int target = Math.max(0, longTokens - excess);
            int after = trimLongTermMemory(context, target);
            context.refreshTokens();
            excess -= Math.max(0, longTokens - after);
        }
        int domainTokens = context.getCurrentTokens().getOrDefault(ContextSection.DOMAIN_KNOWLEDGE, 0);
        if (domainTokens > 0 && excess > 0) {
            int target = Math.max(0, domainTokens - excess);
            int after = trimDomainKnowledge(context, target);
            context.refreshTokens();
            excess -= Math.max(0, domainTokens - after);
        }
        return Math.max(0, excess);
    }

    private int trimLongTermMemory(TrimContext context, int targetTokens) {
        LongTermMemory memory = context.getSnapshot().getLongTermMemory();
        if (memory == null || memory.getMemoryRefs() == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        List<MemoryRef> refs = mutableCopy(memory.getMemoryRefs());
        int currentTokens = estimator.estimateLongTermMemoryTokens(refs);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<MemoryRef> ordered = new ArrayList<>(refs);
        ordered.sort(Comparator
                .comparing((MemoryRef ref) -> ref != null && ref.getScore() != null ? ref.getScore() : 0.0)
                .thenComparing(ref -> ref != null && ref.getExpiresAt() != null ? ref.getExpiresAt() : Instant.MAX)
                .thenComparingInt(ref -> ref != null ? estimator.safeLength(ref.getSnippet()) : 0));
        for (MemoryRef ref : ordered) {
            if (currentTokens <= targetTokens) {
                break;
            }
            if (!refs.remove(ref)) {
                continue;
            }
            int removedChars = estimator.estimateMemoryRefChars(ref);
            int removedTokens = estimator.estimateMemoryRefTokens(ref);
            recordRemoved(context, ContextSection.LONG_TERM_MEMORY, 1, removedChars, removedTokens);
            currentTokens = estimator.estimateLongTermMemoryTokens(refs);
        }
        memory.setMemoryRefs(refs.isEmpty() ? null : refs);
        return estimator.estimateLongTermMemoryTokens(refs);
    }

    private int trimDomainKnowledge(TrimContext context, int targetTokens) {
        DomainKnowledge knowledge = context.getSnapshot().getDomainKnowledge();
        if (knowledge == null || knowledge.getCitations() == null) {
            return 0;
        }
        ContextTokenEstimator estimator = context.getEstimator();
        List<Citation> citations = mutableCopy(knowledge.getCitations());
        int currentTokens = estimator.estimateDomainKnowledgeTokens(citations);
        if (currentTokens <= targetTokens) {
            return currentTokens;
        }
        List<Citation> ordered = new ArrayList<>(citations);
        ordered.sort(Comparator
                .comparing((Citation citation) -> citation != null && citation.getFetchedAt() != null
                        ? citation.getFetchedAt() : Instant.MAX)
                .thenComparingInt(citation -> citation != null ? estimator.safeLength(citation.getSnippet()) : 0)
                .thenComparingInt(citation -> citation != null ? estimator.safeLength(citation.getTitle()) : 0));
        for (Citation citation : ordered) {
            if (currentTokens <= targetTokens) {
                break;
            }
            if (!citations.remove(citation)) {
                continue;
            }
            int removedChars = estimator.estimateCitationChars(citation);
            int removedTokens = estimator.estimateCitationTokens(citation);
            recordRemoved(context, ContextSection.DOMAIN_KNOWLEDGE, 1, removedChars, removedTokens);
            currentTokens = estimator.estimateDomainKnowledgeTokens(citations);
        }
        knowledge.setCitations(citations.isEmpty() ? null : citations);
        return estimator.estimateDomainKnowledgeTokens(citations);
    }
}
