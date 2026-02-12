package com.example.agent.streaming.payload;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.trim.model.ContextCompressionResult;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.model.ContextTrimStats;
import com.example.agent.budget.trim.model.PrunedItem;
import com.example.agent.capabilities.context.model.BuildMetrics;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.DomainKnowledge;
import com.example.agent.capabilities.context.evidence.EvidencePack;
import com.example.agent.capabilities.context.evidence.EvidenceStats;
import com.example.agent.capabilities.context.model.LongTermMemory;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.RuntimeMeta;
import com.example.agent.capabilities.context.model.TaskIntent;
import com.example.agent.capabilities.context.model.ToolState;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.example.agent.streaming.sse.EventStreamService;

/**
 * 上下文事件发布器。
 */
@Service
public class ContextEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContextEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final MetricsPublisher metricsPublisher;

    public ContextEventPublisher(ApplicationEventPublisher eventPublisher,
                                 EventStreamService eventStreamService,
                                 MetricsPublisher metricsPublisher) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 发布上下文快照事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param snapshot 上下文快照
     * @param allocation 预算分配结果
     * @param pruneResult 裁剪结果
     */
    public void publishSnapshot(TenantContext tenantContext,
                                String workflowId,
                                AtomicLong seqCounter,
                                ContextSnapshot snapshot,
                                ContextBudgetAllocation allocation,
                                ContextPruneResult pruneResult,
                                BuildMetrics metrics) {
        if (tenantContext == null || snapshot == null || workflowId == null) {
            return;
        }
        ContextSnapshotSummary snapshotSummary = buildSnapshotSummary(snapshot);
        ContextBudgetSummary budgetSummary = buildBudgetSummary(allocation);
        ContextPruneSummary pruneSummary = buildPruneSummary(pruneResult);
        SummaryStats summaryStats = resolveSummaryStats(snapshot);
        EvidenceStatsSummary evidenceStats = resolveEvidenceStats(snapshot);
        List<String> sections = snapshotSummary != null ? snapshotSummary.getSections() : resolveSections(snapshot);

        ContextDelta snapshotDelta = buildDelta(sections, null);
        Map<String, Object> payload = buildPayload(snapshot, snapshotSummary, budgetSummary, null, metrics, summaryStats,
                evidenceStats);
        payload.put("delta", snapshotDelta);
        publishEvent(tenantContext, workflowId, seqCounter, EventType.CONTEXT_SNAPSHOT_CREATED, payload);

        if (pruneSummary != null && pruneSummary.getRemovedCount() != null
                && pruneSummary.getRemovedCount() > 0) {
            ContextDelta pruneDelta = buildDelta(sections, pruneSummary);
            Map<String, Object> prunePayload = buildPayload(snapshot, snapshotSummary, budgetSummary, pruneSummary,
                    metrics, summaryStats, evidenceStats);
            prunePayload.put("delta", pruneDelta);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.CONTEXT_PRUNED, prunePayload);
        }
    }

    /**
     * 发布上下文快照阶段事件。
     *
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序列计数器
     * @param snapshot 上下文快照，可为空
     * @param snapshotId 快照标识，可为空
     * @param allocation 预算分配，可为空
     * @param trimReport 裁剪报告，可为空
     * @param compressionResult 压缩结果，可为空
     * @param promptTruncatedSections 提示词裁剪段落标识
     * @param stage 快照阶段
     * @param beforeTokens 阶段前 token 估算
     * @param afterTokens 阶段后 token 估算
     */
    public void publishSnapshotStage(TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     ContextSnapshot snapshot,
                                     String snapshotId,
                                     ContextBudgetAllocation allocation,
                                     ContextTrimReport trimReport,
                                     ContextCompressionResult compressionResult,
                                     List<String> promptTruncatedSections,
                                     ContextSnapshotStage stage,
                                     Integer beforeTokens,
                                     Integer afterTokens) {
        if (tenantContext == null || workflowId == null || stage == null) {
            return;
        }
        String resolvedSnapshotId = snapshotId;
        if ((resolvedSnapshotId == null || resolvedSnapshotId.isBlank()) && snapshot != null) {
            resolvedSnapshotId = snapshot.getSnapshotId();
        }

        ContextSnapshotEventPayload payload = new ContextSnapshotEventPayload();
        payload.setTenantId(tenantContext.getTenantId());
        payload.setWorkflowId(workflowId);
        if (resolvedSnapshotId != null && !resolvedSnapshotId.isBlank()) {
            payload.setSnapshotId(resolvedSnapshotId);
        }
        payload.setStage(stage);
        payload.setBudgetSummary(buildBudgetSummary(allocation));
        payload.setTrimSummary(buildTrimSummary(trimReport));
        payload.setCompressionSummary(buildCompressionSummary(compressionResult));
        payload.setPromptTruncatedSections(promptTruncatedSections);
        fillEvidenceStats(payload, snapshot);

        Map<String, Object> payloadMap = buildStagePayload(payload);
        publishEvent(tenantContext, workflowId, seqCounter, EventType.CONTEXT_SNAPSHOT_STAGE, payloadMap);

        int truncatedCount = promptTruncatedSections != null ? promptTruncatedSections.size() : 0;
        log.info("上下文快照阶段事件, tenantId={}, workflowId={}, stage={}, snapshotId={}, beforeTokens={}, afterTokens={}, "
                        + "truncatedSectionsCount={}",
                tenantContext.getTenantId(),
                workflowId,
                stage,
                resolvedSnapshotId,
                beforeTokens,
                afterTokens,
                truncatedCount);
        recordStageMetrics(stage, payloadMap);
    }

    private List<String> resolveSections(ContextSnapshot snapshot) {
        List<String> sections = new java.util.ArrayList<>();
        if (snapshot.getRuntimeMeta() != null) {
            sections.add("runtime");
        }
        if (snapshot.getRoleBoundary() != null) {
            sections.add("role");
        }
        if (snapshot.getTaskIntent() != null) {
            sections.add("intent");
        }
        if (snapshot.getWorkingMemory() != null) {
            sections.add("working");
        }
        if (snapshot.getDomainKnowledge() != null) {
            sections.add("knowledge");
        }
        if (snapshot.getLongTermMemory() != null) {
            sections.add("long_term");
        }
        if (snapshot.getToolState() != null) {
            sections.add("tool");
        }
        if (snapshot.getBudgetState() != null) {
            sections.add("budget");
        }
        return sections;
    }

    private Map<String, Object> buildPayload(ContextSnapshot snapshot,
                                             ContextSnapshotSummary snapshotSummary,
                                             ContextBudgetSummary budgetSummary,
                                             ContextPruneSummary pruneSummary,
                                             BuildMetrics metrics,
                                             SummaryStats summaryStats,
                                             EvidenceStatsSummary evidenceStats) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("snapshotId", snapshot.getSnapshotId());
        if (snapshotSummary != null) {
            payload.put("snapshotSummary", snapshotSummary);
        }
        if (budgetSummary != null) {
            payload.put("budgetSummary", budgetSummary);
        }
        if (pruneSummary != null) {
            payload.put("pruneSummary", pruneSummary);
        }
        if (snapshot.getBudgetState() != null) {
            payload.put("budgetState", snapshot.getBudgetState());
        }
        if (snapshot.getToolState() != null) {
            payload.put("toolState", snapshot.getToolState());
        }
        if (metrics != null) {
            payload.put("buildMetrics", metrics);
        }
        if (snapshot.getAuditMetadata() != null) {
            payload.put("auditMetadata", snapshot.getAuditMetadata());
        }
        if (summaryStats != null) {
            payload.put("usedStructuredSummary", summaryStats.isUsedStructuredSummary());
            payload.put("summaryVersion", summaryStats.getSummaryVersion());
            payload.put("summaryChars", summaryStats.getSummaryChars());
            payload.put("workingMemoryItems", summaryStats.getWorkingMemoryItems());
        }
        if (evidenceStats != null) {
            payload.put("evidencePackPresent", evidenceStats.isPresent());
            payload.put("evidenceToolCount", evidenceStats.getToolCount());
            payload.put("evidenceMemoryCount", evidenceStats.getMemoryCount());
            payload.put("evidenceResearchCount", evidenceStats.getResearchCount());
            payload.put("evidenceTruncationCount", evidenceStats.getTruncationCount());
            payload.put("evidenceApproxChars", evidenceStats.getApproxChars());
            if (evidenceStats.isPresent() && evidenceStats.getVersion() != null
                    && !evidenceStats.getVersion().isBlank()) {
                payload.put("evidencePackVersion", evidenceStats.getVersion());
            }
        }
        return payload;
    }

    private ContextTrimSummary buildTrimSummary(ContextTrimReport trimReport) {
        if (trimReport == null) {
            return null;
        }
        ContextTrimSummary summary = new ContextTrimSummary();
        summary.setVersion(trimReport.getVersion());
        summary.setBeforeTokens(trimReport.getTotalBeforeTokens());
        summary.setAfterTokens(trimReport.getTotalAfterTokens());
        if (trimReport.getRemovedItemsBySection() != null && !trimReport.getRemovedItemsBySection().isEmpty()) {
            Map<String, ContextTrimStats> removedBySection = new HashMap<>();
            for (Map.Entry<com.example.agent.budget.core.ContextSection, ContextTrimStats> entry
                    : trimReport.getRemovedItemsBySection().entrySet()) {
                if (entry.getKey() != null) {
                    removedBySection.put(entry.getKey().name(), entry.getValue());
                }
            }
            summary.setRemovedBySection(removedBySection.isEmpty() ? null : removedBySection);
        }
        summary.setReasons(trimReport.getReasons());
        return summary;
    }

    private ContextCompressionSummary buildCompressionSummary(ContextCompressionResult result) {
        if (result == null) {
            return null;
        }
        if (!result.isTriggered()) {
            return null;
        }
        ContextCompressionSummary summary = new ContextCompressionSummary();
        summary.setTriggerReason(result.getTriggerReason());
        Integer beforeTokens = result.getAfterTrimTokens() != null
                ? result.getAfterTrimTokens()
                : result.getBeforeTokens();
        summary.setBeforeTokens(beforeTokens);
        summary.setAfterTokens(result.getAfterCompressTokens());
        summary.setDurationMs(result.getDurationMs());
        summary.setSummaryVersion(result.getSummaryVersion());
        summary.setWinnerSource(result.getWinnerSource());
        summary.setRollbackApplied(result.isRollbackApplied());
        summary.setRollbackReason(result.getRollbackReason());
        summary.setRolloutVersion(result.getRolloutVersion());
        summary.setQualityGateVersion(result.getQualityGateVersion());
        summary.setRollbackPolicyVersion(result.getRollbackPolicyVersion());
        summary.setQualityScore(result.getQualityScore());
        return summary;
    }

    private void fillEvidenceStats(ContextSnapshotEventPayload payload, ContextSnapshot snapshot) {
        if (payload == null) {
            return;
        }
        EvidenceStatsSummary evidenceStats = resolveEvidenceStats(snapshot);
        if (evidenceStats == null) {
            return;
        }
        payload.setEvidencePackPresent(evidenceStats.isPresent());
        payload.setEvidenceToolCount(evidenceStats.getToolCount());
        payload.setEvidenceMemoryCount(evidenceStats.getMemoryCount());
        payload.setEvidenceResearchCount(evidenceStats.getResearchCount());
        payload.setEvidenceTruncationCount(evidenceStats.getTruncationCount());
        payload.setEvidenceApproxChars(evidenceStats.getApproxChars());
        payload.setEvidencePackVersion(evidenceStats.getVersion());
    }

    private Map<String, Object> buildStagePayload(ContextSnapshotEventPayload payload) {
        Map<String, Object> map = new HashMap<>();
        if (payload == null) {
            return map;
        }
        if (payload.getTenantId() != null) {
            map.put("tenantId", payload.getTenantId());
        }
        if (payload.getWorkflowId() != null) {
            map.put("workflowId", payload.getWorkflowId());
        }
        if (payload.getSnapshotId() != null) {
            map.put("snapshotId", payload.getSnapshotId());
        }
        if (payload.getStage() != null) {
            map.put("stage", payload.getStage().name());
        }
        if (payload.getBudgetSummary() != null) {
            map.put("budgetSummary", payload.getBudgetSummary());
        }
        if (payload.getTrimSummary() != null) {
            map.put("trimSummary", payload.getTrimSummary());
        }
        if (payload.getCompressionSummary() != null) {
            map.put("compressionSummary", payload.getCompressionSummary());
            // 字段展开：压缩阶段将治理关键字段平铺，便于下游检索与告警路由。
            appendCompressionGovernanceFields(map, payload.getCompressionSummary());
        }
        if (payload.getPromptTruncatedSections() != null) {
            map.put("promptTruncatedSections", payload.getPromptTruncatedSections());
        }
        if (payload.getEvidencePackPresent() != null) {
            map.put("evidencePackPresent", payload.getEvidencePackPresent());
        }
        if (payload.getEvidenceToolCount() != null) {
            map.put("evidenceToolCount", payload.getEvidenceToolCount());
        }
        if (payload.getEvidenceMemoryCount() != null) {
            map.put("evidenceMemoryCount", payload.getEvidenceMemoryCount());
        }
        if (payload.getEvidenceResearchCount() != null) {
            map.put("evidenceResearchCount", payload.getEvidenceResearchCount());
        }
        if (payload.getEvidenceTruncationCount() != null) {
            map.put("evidenceTruncationCount", payload.getEvidenceTruncationCount());
        }
        if (payload.getEvidenceApproxChars() != null) {
            map.put("evidenceApproxChars", payload.getEvidenceApproxChars());
        }
        if (payload.getEvidencePackVersion() != null) {
            map.put("evidencePackVersion", payload.getEvidencePackVersion());
        }
        return map;
    }

    private void recordStageMetrics(ContextSnapshotStage stage, Map<String, Object> payload) {
        if (metricsPublisher == null || stage == null) {
            return;
        }
        metricsPublisher.incrementWithTags("context_snapshot_events_total", "stage", stage.name());
        if (payload != null) {
            metricsPublisher.recordSummary("context_snapshot_event_payload_chars", payload.toString().length());
            // 压缩阶段指标：补充回滚与赢家来源标签，支撑治理效果观测。
            recordCompressionStageMetrics(stage, payload);
        }
    }

    /**
     * 展开压缩治理字段到事件载荷。
     */
    private void appendCompressionGovernanceFields(Map<String, Object> map, ContextCompressionSummary summary) {
        // 参数判定：任一对象为空时直接返回，避免写入空字段。
        if (map == null || summary == null) {
            return;
        }
        // 字段写入：赢家来源用于区分最终压缩生效链路。
        if (summary.getWinnerSource() != null) {
            map.put("compressionWinnerSource", summary.getWinnerSource());
        }
        // 字段写入：回滚标记用于区分是否触发自动回滚。
        if (summary.getRollbackApplied() != null) {
            map.put("compressionRollbackApplied", summary.getRollbackApplied());
        }
        // 字段写入：回滚原因用于故障定位。
        if (summary.getRollbackReason() != null) {
            map.put("compressionRollbackReason", summary.getRollbackReason());
        }
        // 字段写入：灰度版本用于对齐灰度策略发布。
        if (summary.getRolloutVersion() != null) {
            map.put("compressionRolloutVersion", summary.getRolloutVersion());
        }
        // 字段写入：质量门禁版本用于策略对账。
        if (summary.getQualityGateVersion() != null) {
            map.put("compressionQualityGateVersion", summary.getQualityGateVersion());
        }
        // 字段写入：回滚策略版本用于审计追踪。
        if (summary.getRollbackPolicyVersion() != null) {
            map.put("compressionRollbackPolicyVersion", summary.getRollbackPolicyVersion());
        }
        // 字段写入：质量分用于趋势分析。
        if (summary.getQualityScore() != null) {
            map.put("compressionQualityScore", summary.getQualityScore());
        }
    }

    /**
     * 记录压缩阶段细粒度指标。
     */
    private void recordCompressionStageMetrics(ContextSnapshotStage stage, Map<String, Object> payload) {
        // 阶段判定：仅在压缩阶段记录治理细粒度指标。
        if (stage != ContextSnapshotStage.CONTEXT_COMPRESSED) {
            return;
        }
        // 载荷判定：缺失载荷时直接返回。
        if (payload == null) {
            return;
        }
        Object winnerSource = payload.get("compressionWinnerSource");
        // 来源指标：按赢家来源统计压缩生效链路。
        if (winnerSource instanceof String source && !source.isBlank()) {
            metricsPublisher.incrementWithTags("context_compression_winner_total", "source", source);
        }
        Object rollbackApplied = payload.get("compressionRollbackApplied");
        // 回滚指标：按回滚是否触发计数。
        if (rollbackApplied instanceof Boolean applied) {
            metricsPublisher.incrementWithTags(
                    "context_compression_rollback_total",
                    "applied",
                    Boolean.toString(applied));
        }
        Object rollbackReason = payload.get("compressionRollbackReason");
        // 回滚原因指标：记录触发回滚的原因分布。
        if (rollbackReason instanceof String reason && !reason.isBlank()) {
            metricsPublisher.incrementWithTags("context_compression_rollback_reason_total", "reason", reason);
        }
        Object qualityScore = payload.get("compressionQualityScore");
        // 质量分指标：记录压缩质量分布，支撑策略调优。
        if (qualityScore instanceof Number score) {
            metricsPublisher.recordSummary("context_compression_quality_score", score.doubleValue());
        }
    }

    private ContextSnapshotSummary buildSnapshotSummary(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        ContextSnapshotSummary summary = new ContextSnapshotSummary();
        List<String> sections = resolveSections(snapshot);
        summary.setSections(sections == null || sections.isEmpty() ? null : sections);

        TaskIntent taskIntent = snapshot.getTaskIntent();
        if (taskIntent != null) {
            summary.setTaskId(taskIntent.getTaskId());
            summary.setInputSize(resolveLength(taskIntent.getInputText()));
            summary.setConstraintCount(resolveSize(taskIntent.getConstraints()));
        }

        RoleBoundary roleBoundary = snapshot.getRoleBoundary();
        if (roleBoundary != null) {
            summary.setApprovalRequired(roleBoundary.getApprovalRequired());
            summary.setRiskLevel(roleBoundary.getRiskLevel());
        }

        WorkingMemory workingMemory = snapshot.getWorkingMemory();
        if (workingMemory != null) {
            summary.setWorkingSummarySize(resolveLength(workingMemory.getSummary()));
            summary.setKeyFactCount(resolveSize(workingMemory.getKeyFacts()));
            summary.setPlanStepCount(resolveSize(workingMemory.getPlanSteps()));
            summary.setRecentToolCallCount(resolveSize(workingMemory.getRecentToolCalls()));
            if (workingMemory.getEvidencePack() != null) {
                summary.setEvidenceCount(resolveSize(workingMemory.getEvidencePack().getEvidences()));
            }
        }

        DomainKnowledge domainKnowledge = snapshot.getDomainKnowledge();
        if (domainKnowledge != null) {
            summary.setCitationCount(resolveSize(domainKnowledge.getCitations()));
        }

        LongTermMemory longTermMemory = snapshot.getLongTermMemory();
        if (longTermMemory != null) {
            summary.setMemoryCount(resolveSize(longTermMemory.getMemoryRefs()));
        }

        ToolState toolState = snapshot.getToolState();
        if (toolState != null) {
            summary.setToolCount(resolveSize(toolState.getAvailableTools()));
            summary.setSelectedToolCount(resolveSize(toolState.getSelectedTools()));
        }

        RuntimeMeta runtimeMeta = snapshot.getRuntimeMeta();
        if (runtimeMeta != null) {
            summary.setAllowedToolCount(resolveSize(runtimeMeta.getAllowedTools()));
        }

        return summary;
    }

    private SummaryStats resolveSummaryStats(ContextSnapshot snapshot) {
        SummaryStats stats = new SummaryStats();
        if (snapshot == null || snapshot.getWorkingMemory() == null) {
            return stats;
        }
        WorkingMemory memory = snapshot.getWorkingMemory();
        boolean usedStructuredSummary = Boolean.TRUE.equals(memory.getUsedStructuredSummary());
        String summaryVersion = memory.getSummaryVersion();
        if ((summaryVersion == null || summaryVersion.isBlank()) && usedStructuredSummary) {
            summaryVersion = "v1";
        }
        Integer summaryChars = memory.getSummaryChars();
        if (summaryChars == null) {
            summaryChars = memory.getSummary() != null ? memory.getSummary().length() : 0;
        }
        Integer workingMemoryItems = memory.getWorkingMemoryItems();
        if (workingMemoryItems == null) {
            workingMemoryItems = memory.getKeyFacts() != null ? memory.getKeyFacts().size() : 0;
        }
        stats.setUsedStructuredSummary(usedStructuredSummary);
        stats.setSummaryVersion(summaryVersion);
        stats.setSummaryChars(summaryChars);
        stats.setWorkingMemoryItems(workingMemoryItems);
        return stats;
    }

    private EvidenceStatsSummary resolveEvidenceStats(ContextSnapshot snapshot) {
        EvidenceStatsSummary stats = new EvidenceStatsSummary();
        if (snapshot == null || snapshot.getWorkingMemory() == null) {
            return stats;
        }
        EvidencePack pack = snapshot.getWorkingMemory().getEvidencePack();
        if (pack == null) {
            return stats;
        }
        EvidenceStats packStats = pack.getStats();
        if (packStats == null) {
            packStats = pack.recomputeStats();
        }
        stats.setPresent(true);
        if (pack.getVersion() != null && !pack.getVersion().isBlank()) {
            stats.setVersion(pack.getVersion());
        }
        stats.setToolCount(resolveCount(packStats != null ? packStats.getToolCount() : null));
        stats.setMemoryCount(resolveCount(packStats != null ? packStats.getMemoryCount() : null));
        stats.setResearchCount(resolveCount(packStats != null ? packStats.getResearchCount() : null));
        stats.setTruncationCount(resolveCount(packStats != null ? packStats.getTruncationCount() : null));
        stats.setApproxChars(resolveCount(packStats != null ? packStats.getApproxChars() : null));
        return stats;
    }

    private ContextBudgetSummary buildBudgetSummary(ContextBudgetAllocation allocation) {
        if (allocation == null) {
            allocation = ContextBudgetAllocation.disabled(
                    ContextBudgetAllocationState.DISABLED_BY_DEPENDENCY,
                    "missing_allocation"
            );
        }
        ContextBudgetSummary summary = new ContextBudgetSummary();
        summary.setAllocationState(allocation.getAllocationState());
        summary.setAllocationReason(allocation.getAllocationReason());
        summary.setTotalTokens(allocation.getTotalTokens());
        summary.setReservedTokens(allocation.getReservedTokens());
        if (allocation.getSectionTokens() != null && !allocation.getSectionTokens().isEmpty()) {
            Map<String, Integer> sectionTokens = new HashMap<>();
            for (Map.Entry<ContextSection, Integer> entry : allocation.getSectionTokens().entrySet()) {
                if (entry.getKey() != null) {
                    sectionTokens.put(entry.getKey().name(), entry.getValue());
                }
            }
            summary.setSectionTokens(sectionTokens.isEmpty() ? null : sectionTokens);
        }
        return summary;
    }

    private ContextPruneSummary buildPruneSummary(ContextPruneResult pruneResult) {
        if (pruneResult == null) {
            return null;
        }
        List<PrunedItem> removedItems = pruneResult.getRemovedItems();
        ContextPruneSummary summary = new ContextPruneSummary();
        if (removedItems != null && !removedItems.isEmpty()) {
            summary.setRemovedCount(removedItems.size());
            summary.setRemovedItemTypes(resolveRemovedItemTypes(removedItems));
        }
        summary.setSummary(pruneResult.getSummary());
        return summary;
    }

    private ContextDelta buildDelta(List<String> sections, ContextPruneSummary pruneSummary) {
        ContextDelta delta = new ContextDelta();
        if (sections != null && !sections.isEmpty()) {
            delta.setChangedSections(sections);
        }
        if (pruneSummary != null) {
            delta.setSummary(pruneSummary.getSummary());
            delta.setRemovedCount(pruneSummary.getRemovedCount());
            delta.setRemovedItemTypes(pruneSummary.getRemovedItemTypes());
        }
        return delta;
    }

    private Map<String, Integer> resolveRemovedItemTypes(List<PrunedItem> removedItems) {
        if (removedItems == null || removedItems.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (PrunedItem item : removedItems) {
            if (item == null) {
                continue;
            }
            String type = item.getItemType();
            if (type == null || type.isBlank()) {
                type = "unknown";
            }
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return counts.isEmpty() ? null : counts;
    }

    private Integer resolveSize(Collection<?> items) {
        if (items == null) {
            return null;
        }
        return items.size();
    }

    private Integer resolveLength(String value) {
        if (value == null) {
            return null;
        }
        return value.length();
    }

    private int resolveCount(Integer value) {
        return value == null ? 0 : value;
    }

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        long seq = seqCounter != null
                ? seqCounter.incrementAndGet()
                : eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        Instant eventTime = Instant.now();
        event.setTimestamp(eventTime);
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        payload.putIfAbsent("eventId", event.getEventId());
        payload.putIfAbsent("eventType", type != null ? type.name() : null);
        payload.putIfAbsent("eventTime", eventTime);
        payload.putIfAbsent("traceId", tenantContext.getTraceId());
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
        SummaryStats summaryStats = resolveSummaryStats(payload);
        EvidenceStatsSummary evidenceStats = resolveEvidenceStats(payload);
        Object snapshotId = payload != null ? payload.get("snapshotId") : null;
        log.info("上下文事件统计, tenantId={}, workflowId={}, snapshotId={}, eventType={}, usedStructuredSummary={}, "
                        + "summaryVersion={}, summaryChars={}, workingMemoryItems={}, evidenceToolCount={}, "
                        + "evidenceMemoryCount={}, evidenceResearchCount={}, evidenceTruncationCount={}, evidenceApproxChars={}",
                tenantContext.getTenantId(),
                workflowId,
                snapshotId,
                type,
                summaryStats.isUsedStructuredSummary(),
                summaryStats.getSummaryVersion(),
                summaryStats.getSummaryChars(),
                summaryStats.getWorkingMemoryItems(),
                evidenceStats.getToolCount(),
                evidenceStats.getMemoryCount(),
                evidenceStats.getResearchCount(),
                evidenceStats.getTruncationCount(),
                evidenceStats.getApproxChars());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
        log.debug("上下文事件发布, type={}, workflowId={}, seq={}", type, workflowId, seq);
    }

    private SummaryStats resolveSummaryStats(Map<String, Object> payload) {
        SummaryStats stats = new SummaryStats();
        if (payload == null || payload.isEmpty()) {
            return stats;
        }
        Object usedStructured = payload.get("usedStructuredSummary");
        Object version = payload.get("summaryVersion");
        Object summaryChars = payload.get("summaryChars");
        Object workingItems = payload.get("workingMemoryItems");
        if (usedStructured instanceof Boolean bool) {
            stats.setUsedStructuredSummary(bool);
        }
        if (version instanceof String text && !text.isBlank()) {
            stats.setSummaryVersion(text);
        }
        if (summaryChars instanceof Number number) {
            stats.setSummaryChars(number.intValue());
        }
        if (workingItems instanceof Number number) {
            stats.setWorkingMemoryItems(number.intValue());
        }
        return stats;
    }

    private EvidenceStatsSummary resolveEvidenceStats(Map<String, Object> payload) {
        EvidenceStatsSummary stats = new EvidenceStatsSummary();
        if (payload == null || payload.isEmpty()) {
            return stats;
        }
        Object present = payload.get("evidencePackPresent");
        Object version = payload.get("evidencePackVersion");
        Object toolCount = payload.get("evidenceToolCount");
        Object memoryCount = payload.get("evidenceMemoryCount");
        Object researchCount = payload.get("evidenceResearchCount");
        Object truncationCount = payload.get("evidenceTruncationCount");
        Object approxChars = payload.get("evidenceApproxChars");
        if (present instanceof Boolean bool) {
            stats.setPresent(bool);
        }
        if (version instanceof String text && !text.isBlank()) {
            stats.setVersion(text);
        }
        if (toolCount instanceof Number number) {
            stats.setToolCount(number.intValue());
        }
        if (memoryCount instanceof Number number) {
            stats.setMemoryCount(number.intValue());
        }
        if (researchCount instanceof Number number) {
            stats.setResearchCount(number.intValue());
        }
        if (truncationCount instanceof Number number) {
            stats.setTruncationCount(number.intValue());
        }
        if (approxChars instanceof Number number) {
            stats.setApproxChars(number.intValue());
        }
        return stats;
    }

    /**
     * 结构化摘要统计信息，用于事件载荷补齐与日志输出。
     */
    private static class SummaryStats {

        private boolean usedStructuredSummary;
        private String summaryVersion;
        private int summaryChars;
        private int workingMemoryItems;

        public boolean isUsedStructuredSummary() {
            return usedStructuredSummary;
        }

        public void setUsedStructuredSummary(boolean usedStructuredSummary) {
            this.usedStructuredSummary = usedStructuredSummary;
        }

        public String getSummaryVersion() {
            return summaryVersion;
        }

        public void setSummaryVersion(String summaryVersion) {
            this.summaryVersion = summaryVersion;
        }

        public int getSummaryChars() {
            return summaryChars;
        }

        public void setSummaryChars(int summaryChars) {
            this.summaryChars = summaryChars;
        }

        public int getWorkingMemoryItems() {
            return workingMemoryItems;
        }

        public void setWorkingMemoryItems(int workingMemoryItems) {
            this.workingMemoryItems = workingMemoryItems;
        }
    }

    /**
     * 证据包统计信息，用于事件载荷补齐与日志输出。
     */
    private static class EvidenceStatsSummary {

        private boolean present;
        private String version;
        private int toolCount;
        private int memoryCount;
        private int researchCount;
        private int truncationCount;
        private int approxChars;

        public boolean isPresent() {
            return present;
        }

        public void setPresent(boolean present) {
            this.present = present;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public int getToolCount() {
            return toolCount;
        }

        public void setToolCount(int toolCount) {
            this.toolCount = toolCount;
        }

        public int getMemoryCount() {
            return memoryCount;
        }

        public void setMemoryCount(int memoryCount) {
            this.memoryCount = memoryCount;
        }

        public int getResearchCount() {
            return researchCount;
        }

        public void setResearchCount(int researchCount) {
            this.researchCount = researchCount;
        }

        public int getTruncationCount() {
            return truncationCount;
        }

        public void setTruncationCount(int truncationCount) {
            this.truncationCount = truncationCount;
        }

        public int getApproxChars() {
            return approxChars;
        }

        public void setApproxChars(int approxChars) {
            this.approxChars = approxChars;
        }
    }
}
