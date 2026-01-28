package com.example.agent.budget;

import com.example.agent.context.ContextPolicy;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.DomainKnowledge;
import com.example.agent.context.EvidenceItem;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.LongTermMemory;
import com.example.agent.context.MemoryRef;
import com.example.agent.context.WorkingMemory;
import com.example.agent.memory.TokenEstimator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认上下文裁剪器。
 */
@Service
public class DefaultContextPruner implements ContextPruner {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextPruner.class);

    private final TokenEstimator tokenEstimator;

    public DefaultContextPruner(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public ContextPruneResult prune(ContextPruneRequest request) {
        ContextPruneResult result = new ContextPruneResult();
        if (request == null || request.getSnapshot() == null) {
            return result;
        }
        ContextSnapshot snapshot = request.getSnapshot();
        List<PrunedItem> removedItems = new ArrayList<>();
        ContextPolicy policy = request.getPolicy();

        pruneMemoryRefs(snapshot.getLongTermMemory(), policy, removedItems);
        pruneEvidencePack(snapshot.getWorkingMemory(), policy, removedItems);
        pruneCitations(snapshot.getDomainKnowledge(), policy, removedItems);
        pruneWorkingSummary(snapshot.getWorkingMemory(), request.getAllocation(), removedItems);

        result.setPrunedSnapshot(snapshot);
        result.setRemovedItems(removedItems.isEmpty() ? null : removedItems);
        result.setSummary(buildSummary(removedItems));
        if (!removedItems.isEmpty()) {
            log.info("上下文裁剪完成, removedCount={}", removedItems.size());
        }
        return result;
    }

    private void pruneMemoryRefs(LongTermMemory memory, ContextPolicy policy, List<PrunedItem> removedItems) {
        if (memory == null || memory.getMemoryRefs() == null || policy == null) {
            return;
        }
        Integer max = policy.getMaxMemoryCount();
        if (max == null || max <= 0) {
            return;
        }
        List<MemoryRef> refs = memory.getMemoryRefs();
        if (refs.size() <= max) {
            return;
        }
        List<MemoryRef> kept = new ArrayList<>(refs.subList(0, max));
        for (int i = max; i < refs.size(); i++) {
            MemoryRef ref = refs.get(i);
            PrunedItem item = new PrunedItem();
            item.setItemType("memory");
            item.setItemId(ref != null ? ref.getMemoryId() : null);
            item.setReason("memory_limit");
            removedItems.add(item);
        }
        memory.setMemoryRefs(kept);
    }

    private void pruneEvidencePack(WorkingMemory memory, ContextPolicy policy, List<PrunedItem> removedItems) {
        if (memory == null || memory.getEvidencePack() == null || policy == null) {
            return;
        }
        Integer max = policy.getMaxEvidenceCount();
        if (max == null || max <= 0) {
            return;
        }
        EvidencePack pack = memory.getEvidencePack();
        if (pack.getItems() == null || pack.getItems().size() <= max) {
            return;
        }
        List<EvidenceItem> kept = new ArrayList<>(pack.getItems().subList(0, max));
        for (int i = max; i < pack.getItems().size(); i++) {
            EvidenceItem itemValue = pack.getItems().get(i);
            PrunedItem item = new PrunedItem();
            item.setItemType("evidence");
            item.setItemId(itemValue != null ? itemValue.getSourceId() : null);
            item.setReason("evidence_limit");
            removedItems.add(item);
        }
        pack.setItems(kept);
    }

    private void pruneCitations(DomainKnowledge knowledge, ContextPolicy policy, List<PrunedItem> removedItems) {
        if (knowledge == null || knowledge.getCitations() == null || policy == null) {
            return;
        }
        Integer max = policy.getMaxEvidenceCount();
        if (max == null || max <= 0) {
            return;
        }
        if (knowledge.getCitations().size() <= max) {
            return;
        }
        List<com.example.agent.context.Citation> kept = new ArrayList<>(knowledge.getCitations().subList(0, max));
        for (int i = max; i < knowledge.getCitations().size(); i++) {
            com.example.agent.context.Citation citation = knowledge.getCitations().get(i);
            PrunedItem item = new PrunedItem();
            item.setItemType("citation");
            item.setItemId(citation != null ? citation.getSource() : null);
            item.setReason("citation_limit");
            removedItems.add(item);
        }
        knowledge.setCitations(kept);
    }

    private void pruneWorkingSummary(WorkingMemory memory,
                                     ContextBudgetAllocation allocation,
                                     List<PrunedItem> removedItems) {
        if (memory == null || !StringUtils.hasText(memory.getSummary()) || allocation == null) {
            return;
        }
        Map<ContextSection, Integer> sectionTokens = allocation.getSectionTokens();
        if (sectionTokens == null || !sectionTokens.containsKey(ContextSection.WORKING_MEMORY)) {
            return;
        }
        int maxTokens = sectionTokens.get(ContextSection.WORKING_MEMORY);
        if (maxTokens <= 0) {
            return;
        }
        int currentTokens = tokenEstimator.estimateTokens(memory.getSummary());
        if (currentTokens <= maxTokens) {
            return;
        }
        int maxChars = Math.max(4, maxTokens * 4);
        String trimmed = memory.getSummary().substring(0, Math.min(maxChars, memory.getSummary().length()));
        memory.setSummary(trimmed);
        PrunedItem item = new PrunedItem();
        item.setItemType("working_summary");
        item.setItemId("summary");
        item.setReason("budget_limit");
        removedItems.add(item);
    }

    private String buildSummary(List<PrunedItem> removedItems) {
        if (removedItems == null || removedItems.isEmpty()) {
            return null;
        }
        return "removed_items:" + removedItems.size();
    }
}