package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.application.ContextPruner;
import com.example.agent.budget.trim.model.ContextPruneRequest;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.trim.model.PrunedItem;
import com.example.agent.capabilities.context.evidence.EvidenceItem;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.model.ContextPolicy;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.DomainKnowledge;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.MemoryRef;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


/**
 * 默认上下文裁剪器，按照策略对各分段进行裁剪并汇总裁剪结果。
 */
@Service
public class DefaultContextPruner implements ContextPruner {

    /**
     * 分段裁剪处理器接口，负责单个分段的裁剪逻辑。
     */
    @FunctionalInterface
    private interface PruneHandler {

        /**
         * 执行裁剪。
         *
         * @param snapshot 上下文快照
         * @param policy 裁剪策略
         * @param allocation 预算分配
         * @param removedItems 被移除项集合
         */
        void prune(ContextSnapshot snapshot,
                   ContextPolicy policy,
                   ContextBudgetAllocation allocation,
                   List<PrunedItem> removedItems);
    }

    /**
     * 日志记录器，用于输出裁剪流程关键日志。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultContextPruner.class);
    /**
     * 默认裁剪顺序，策略未配置时采用该顺序。
     */
    private static final List<ContextSection> DEFAULT_PRUNE_ORDER = List.of(
            ContextSection.LONG_TERM_MEMORY,
            ContextSection.EVIDENCE_PACK,
            ContextSection.DOMAIN_KNOWLEDGE,
            ContextSection.WORKING_MEMORY);

    /**
     * 令牌估算器，用于计算工作记忆摘要的令牌数量。
     */
    private final TokenEstimator tokenEstimator;
    /**
     * 指标发布器，用于记录裁剪顺序与裁剪过程指标。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 分段裁剪处理器映射，按分段类型定位裁剪实现。
     */
    private final Map<ContextSection, PruneHandler> pruneHandlers;

    /**
     * 构建默认上下文裁剪器。
     *
     * @param tokenEstimator 令牌估算器
     * @param metricsPublisher 指标发布器
     */
    public DefaultContextPruner(TokenEstimator tokenEstimator, MetricsPublisher metricsPublisher) {
        this.tokenEstimator = tokenEstimator;
        this.metricsPublisher = metricsPublisher;
        this.pruneHandlers = initializePruneHandlers();
    }

    /**
     * 按策略与预算执行裁剪并返回裁剪结果。
     *
     * @param request 裁剪请求
     * @return 裁剪结果，包含裁剪后的快照与移除项
     */
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
            PruneHandler handler = pruneHandlers.get(section);
            if (handler != null) {
                handler.prune(snapshot, policy, request.getAllocation(), removedItems);
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
     * 根据策略生成裁剪顺序，缺省时回退为默认顺序。
     *
     * @param policy 裁剪策略
     * @return 解析后的裁剪顺序
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

    /**
     * 解析并规范化分段名称，兼容历史字段并忽略无效值。
     *
     * @param value 分段配置值
     * @return 标准化后的分段枚举，无法解析时返回空值
     */
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
     *
     * @param pruneOrder 裁剪顺序
     * @param snapshot 上下文快照
     * @param policy 裁剪策略
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

    /**
     * 将裁剪顺序转换为指标标签字符串。
     *
     * @param pruneOrder 裁剪顺序
     * @return 指标标签字符串
     */
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

    /**
     * 初始化分段裁剪处理器映射。
     *
     * @return 分段裁剪处理器映射
     */
    private Map<ContextSection, PruneHandler> initializePruneHandlers() {
        Map<ContextSection, PruneHandler> handlers = new EnumMap<>(ContextSection.class);
        handlers.put(ContextSection.LONG_TERM_MEMORY,
                (snapshot, policy, allocation, removedItems) -> pruneMemoryRefs(snapshot.getLongTermMemory(),
                        policy,
                        removedItems));
        handlers.put(ContextSection.EVIDENCE_PACK,
                (snapshot, policy, allocation, removedItems) -> pruneEvidencePack(snapshot.getWorkingMemory(),
                        policy,
                        removedItems));
        handlers.put(ContextSection.DOMAIN_KNOWLEDGE,
                (snapshot, policy, allocation, removedItems) -> pruneCitations(snapshot.getDomainKnowledge(),
                        policy,
                        removedItems));
        handlers.put(ContextSection.WORKING_MEMORY,
                (snapshot, policy, allocation, removedItems) -> pruneWorkingSummary(snapshot.getWorkingMemory(),
                        allocation,
                        removedItems));
        return handlers;
    }

    /**
     * 裁剪长时记忆引用，超出上限的引用将被移除并记录。
     *
     * @param memory 长时记忆
     * @param policy 裁剪策略
     * @param removedItems 被移除项集合
     */
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

    /**
     * 裁剪证据包，超出上限的证据将被移除并记录。
     *
     * @param memory 工作记忆
     * @param policy 裁剪策略
     * @param removedItems 被移除项集合
     */
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

    /**
     * 裁剪领域知识引用，超出上限的引用将被移除并记录。
     *
     * @param knowledge 领域知识
     * @param policy 裁剪策略
     * @param removedItems 被移除项集合
     */
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
        List<com.example.agent.capabilities.context.model.Citation> kept = new ArrayList<>(knowledge.getCitations().subList(0, max));
        for (int i = max; i < knowledge.getCitations().size(); i++) {
            com.example.agent.capabilities.context.model.Citation citation = knowledge.getCitations().get(i);
            PrunedItem item = new PrunedItem();
            item.setItemType("citation");
            item.setItemId(citation != null ? citation.getSource() : null);
            item.setReason("citation_limit");
            removedItems.add(item);
        }
        knowledge.setCitations(kept);
    }

    /**
     * 裁剪工作记忆摘要，基于预算限制摘要长度并记录裁剪项。
     *
     * @param memory 工作记忆
     * @param allocation 预算分配
     * @param removedItems 被移除项集合
     */
    private void pruneWorkingSummary(WorkingMemory memory,
                                     ContextBudgetAllocation allocation,
                                     List<PrunedItem> removedItems) {
        if (memory == null || !StringUtils.hasText(memory.getSummary())
                || !allocation.isAllocationEnabled()) {
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

    /**
     * 构建裁剪摘要信息。
     *
     * @param removedItems 被移除项集合
     * @return 裁剪摘要，未移除时返回空值
     */
    private String buildSummary(List<PrunedItem> removedItems) {
        if (removedItems == null || removedItems.isEmpty()) {
            return null;
        }
        return "removed_items:" + removedItems.size();
    }
}
