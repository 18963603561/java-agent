package com.example.agent.budget.trim;

import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.DomainKnowledge;
import com.example.agent.capabilities.context.EvidenceItem;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.LongTermMemory;
import com.example.agent.capabilities.context.MemoryRef;
import com.example.agent.capabilities.context.WorkingMemory;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.agent.budget.token.ContextBudgetAllocation;

/**
 * 默认上下文裁剪器。
 */
@Service
public class DefaultContextPruner implements ContextPruner {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextPruner.class);
    private static final List<ContextSection> DEFAULT_PRUNE_ORDER = List.of(
            ContextSection.LONG_TERM_MEMORY,
            ContextSection.EVIDENCE_PACK,
            ContextSection.DOMAIN_KNOWLEDGE,
            ContextSection.WORKING_MEMORY);

    private final TokenEstimator tokenEstimator;
    /**
     * 指标发布器，用于记录裁剪顺序。
     */
    private final MetricsPublisher metricsPublisher;

    public DefaultContextPruner(TokenEstimator tokenEstimator, MetricsPublisher metricsPublisher) {
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
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

        List<ContextSection> pruneOrder = resolvePruneOrder(policy);
        recordPruneOrder(pruneOrder, snapshot, policy);
        for (ContextSection section : pruneOrder) {
            switch (section) {
                case LONG_TERM_MEMORY ->
                        pruneMemoryRefs(snapshot.getLongTermMemory(), policy, removedItems);
                case EVIDENCE_PACK ->
                        pruneEvidencePack(snapshot.getWorkingMemory(), policy, removedItems);
                case DOMAIN_KNOWLEDGE ->
                        pruneCitations(snapshot.getDomainKnowledge(), policy, removedItems);
                case WORKING_MEMORY ->
                        pruneWorkingSummary(snapshot.getWorkingMemory(), request.getAllocation(), removedItems);
                default -> {
                }
            }
        }

        result.setPrunedSnapshot(snapshot);
        result.setRemovedItems(removedItems.isEmpty() ? null : removedItems);
        result.setSummary(buildSummary(removedItems));
        if (!removedItems.isEmpty()) {
            log.info("上下文裁剪完成, removedCount={}", removedItems.size());
        }
        return result;
    }

    /**
     * 根据策略生成裁剪顺序，缺失时回退默认顺序。
     */
    private List<ContextSection> resolvePruneOrder(ContextPolicy policy) {
        List<ContextSection> resolved = new ArrayList<>();
        List<String> configured = policy != null ? policy.getPruneOrder() : null;
        if (configured != null) {
            for (String value : configured) {
                ContextSection section = normalizeSection(value);
                if (section != null && !resolved.contains(section)) {
                    resolved.add(section);
                }
            }
        }
        for (ContextSection section : DEFAULT_PRUNE_ORDER) {
            if (!resolved.contains(section)) {
                resolved.add(section);
            }
        }
        return resolved;
    }

    private ContextSection normalizeSection(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if ("TOOL_SUMMARIES".equals(upper)) {
            return ContextSection.TOOL_SUMMARY;
        }
        try {
            return ContextSection.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 记录裁剪顺序的日志与指标。
     */
    private void recordPruneOrder(List<ContextSection> pruneOrder, ContextSnapshot snapshot, ContextPolicy policy) {
        String orderTag = formatPruneOrderTag(pruneOrder);
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags("context_prune_order_used_total", "orderName", orderTag);
        }
        String tenantId = snapshot != null && snapshot.getRuntimeMeta() != null
                ? snapshot.getRuntimeMeta().getTenantId()
                : null;
        String workflowId = snapshot != null && snapshot.getRuntimeMeta() != null
                ? snapshot.getRuntimeMeta().getWorkflowId()
                : null;
        log.info("上下文裁剪顺序, tenantId={}, workflowId={}, hasPolicy={}, pruneOrder={}",
                tenantId, workflowId, policy != null, pruneOrder);
    }

    private String formatPruneOrderTag(List<ContextSection> pruneOrder) {
        if (pruneOrder == null || pruneOrder.isEmpty()) {
            return "default";
        }
        List<String> tags = new ArrayList<>();
        for (ContextSection section : pruneOrder) {
            if (section != null) {
                tags.add(section.name().toLowerCase(Locale.ROOT));
            }
        }
        return tags.isEmpty() ? "default" : String.join(">", tags);
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
        if (pack.getEvidences() == null || pack.getEvidences().size() <= max) {
            return;
        }
        List<EvidenceItem> kept = new ArrayList<>(pack.getEvidences().subList(0, max));
        for (int i = max; i < pack.getEvidences().size(); i++) {
            EvidenceItem itemValue = pack.getEvidences().get(i);
            PrunedItem item = new PrunedItem();
            item.setItemType("evidence");
            item.setItemId(itemValue != null ? itemValue.getEvidenceId() : null);
            item.setReason("evidence_limit");
            removedItems.add(item);
        }
        pack.setEvidences(kept);
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
        List<com.example.agent.capabilities.context.Citation> kept = new ArrayList<>(knowledge.getCitations().subList(0, max));
        for (int i = max; i < knowledge.getCitations().size(); i++) {
            com.example.agent.capabilities.context.Citation citation = knowledge.getCitations().get(i);
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

