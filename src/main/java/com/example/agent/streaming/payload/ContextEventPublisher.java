package com.example.agent.streaming.payload;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CompressionStageEventAssembler compressionStageEventAssembler;

    public ContextEventPublisher(ApplicationEventPublisher eventPublisher,
                                 EventStreamService eventStreamService,
                                 MetricsPublisher metricsPublisher) {
        this(eventPublisher, eventStreamService, metricsPublisher, new CompressionStageEventAssembler());
    }

    /**
     * 构造上下文事件发布器（可注入压缩事件装配器）。
     */
    @Autowired
    public ContextEventPublisher(ApplicationEventPublisher eventPublisher,
                                 EventStreamService eventStreamService,
                                 MetricsPublisher metricsPublisher,
                                 CompressionStageEventAssembler compressionStageEventAssembler) {
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.metricsPublisher = metricsPublisher;
        this.compressionStageEventAssembler = compressionStageEventAssembler;
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

        // 压缩阶段发布：当阶段属于压缩链路时，发布类型化压缩阶段事件契约。
        if (stage.isCompressionStage()) {
            publishCompressionStageEvent(tenantContext,
                    workflowId,
                    seqCounter,
                    snapshot,
                    resolvedSnapshotId,
                    stage,
                    compressionResult,
                    payload);
        }

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

    /**
     * 发布压缩阶段事件。
     */
    private void publishCompressionStageEvent(TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter,
                                              ContextSnapshot snapshot,
                                              String snapshotId,
                                              ContextSnapshotStage stage,
                                              ContextCompressionResult compressionResult,
                                              ContextSnapshotEventPayload stagePayload) {
        // 依赖守卫：缺少装配器时跳过发布，避免影响主快照事件链路。
        if (compressionStageEventAssembler == null) {
            return;
        }
        CompressionStageEventAssembler.EvidenceStatsSummaryView evidenceStats =
                new CompressionStageEventAssembler.EvidenceStatsSummaryView(
                        Boolean.TRUE.equals(stagePayload.getEvidencePackPresent()),
                        stagePayload.getEvidencePackVersion(),
                        safeInt(stagePayload.getEvidenceToolCount()),
                        safeInt(stagePayload.getEvidenceMemoryCount()),
                        safeInt(stagePayload.getEvidenceResearchCount()),
                        safeInt(stagePayload.getEvidenceTruncationCount()),
                        safeInt(stagePayload.getEvidenceApproxChars()));
        // 载荷装配：统一构建压缩阶段事件 payload，确保字段语义稳定。
        CompressionStageEventPayload payload = compressionStageEventAssembler.assemble(
                tenantContext.getTenantId(),
                workflowId,
                snapshotId,
                stage,
                snapshot,
                compressionStageEventAssembler.buildCompressionSummary(compressionResult),
                evidenceStats);
        // 载荷转换：将类型化载荷转换为流式事件 payload map。
        Map<String, Object> payloadMap = buildCompressionStagePayload(payload);
        // 事件发布：发布压缩阶段专用事件，供订阅端精确消费。
        publishEvent(tenantContext, workflowId, seqCounter, EventType.CONTEXT_COMPRESSION_STAGE, payloadMap);
        // 对比发布：当命中双轨实验时发布压缩对比事件，支撑运营侧回放。
        publishCompressionComparisonEvent(tenantContext, workflowId, seqCounter, payload);
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
        summary.setWindowShaped(result.isWindowShaped());
        summary.setShapeReason(result.getShapeReason());
        summary.setPrimersRetained(result.getPrimersRetained());
        summary.setRecentsRetained(result.getRecentsRetained());
        summary.setMiddleWindowSize(result.getMiddleWindowSize());
        summary.setSummaryInjected(result.isSummaryInjected());
        summary.setSummaryInjectReason(result.getSummaryInjectReason());
        summary.setDualTrackEnabled(result.isDualTrackEnabled());
        summary.setRolloutVersion(result.getRolloutVersion());
        summary.setRolloutReason(result.getRolloutReason());
        summary.setPrimarySource(result.getPrimarySource());
        summary.setShadowSource(result.getShadowSource());
        summary.setComparisonRecordId(result.getComparisonRecordId());
        summary.setRollbackReason(result.getRollbackReason());
        summary.setWinnerSource(result.getWinnerSource());
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

    /**
     * 构建压缩阶段事件载荷。
     */
    private Map<String, Object> buildCompressionStagePayload(CompressionStageEventPayload payload) {
        Map<String, Object> map = new HashMap<>();
        // 空值守卫：载荷为空时返回空映射，避免发布异常。
        if (payload == null) {
            return map;
        }
        // 字段映射：按契约顺序写入基础标识字段。
        appendIfPresent(map, "tenantId", payload.getTenantId());
        appendIfPresent(map, "workflowId", payload.getWorkflowId());
        appendIfPresent(map, "snapshotId", payload.getSnapshotId());
        // 阶段映射：阶段存在时写入阶段名称，便于订阅端按字符串过滤。
        if (payload.getStage() != null) {
            map.put("stage", payload.getStage().name());
        }
        // 契约映射：写入压缩摘要及证据统计字段。
        appendIfPresent(map, "compressionSummary", payload.getCompressionSummary());
        appendIfPresent(map, "evidencePackPresent", payload.getEvidencePackPresent());
        appendIfPresent(map, "evidenceToolCount", payload.getEvidenceToolCount());
        appendIfPresent(map, "evidenceMemoryCount", payload.getEvidenceMemoryCount());
        appendIfPresent(map, "evidenceResearchCount", payload.getEvidenceResearchCount());
        appendIfPresent(map, "evidenceTruncationCount", payload.getEvidenceTruncationCount());
        appendIfPresent(map, "evidenceApproxChars", payload.getEvidenceApproxChars());
        appendIfPresent(map, "evidencePackVersion", payload.getEvidencePackVersion());
        return map;
    }

    /**
     * 发布压缩对比事件。
     */
    private void publishCompressionComparisonEvent(TenantContext tenantContext,
                                                   String workflowId,
                                                   AtomicLong seqCounter,
                                                   CompressionStageEventPayload payload) {
        // 条件判定：压缩摘要缺失或未开启双轨时不发布对比事件。
        if (payload == null || payload.getCompressionSummary() == null
                || !payload.getCompressionSummary().isDualTrackEnabled()) {
            return;
        }
        CompressionComparisonEventPayload comparisonPayload = new CompressionComparisonEventPayload();
        comparisonPayload.setTenantId(payload.getTenantId());
        comparisonPayload.setWorkflowId(payload.getWorkflowId());
        comparisonPayload.setSnapshotId(payload.getSnapshotId());
        comparisonPayload.setRolloutVersion(payload.getCompressionSummary().getRolloutVersion());
        comparisonPayload.setPrimarySource(payload.getCompressionSummary().getPrimarySource());
        comparisonPayload.setShadowSource(payload.getCompressionSummary().getShadowSource());
        comparisonPayload.setWinner(payload.getCompressionSummary().getWinnerSource());
        comparisonPayload.setRollbackReason(payload.getCompressionSummary().getRollbackReason());
        comparisonPayload.setComparisonRecordId(payload.getCompressionSummary().getComparisonRecordId());
        // 载荷转换：转换为 map 后发布对比事件。
        publishEvent(tenantContext,
                workflowId,
                seqCounter,
                EventType.CONTEXT_COMPRESSION_COMPARISON,
                buildComparisonPayload(comparisonPayload));
    }

    /**
     * 构建压缩对比事件载荷。
     */
    private Map<String, Object> buildComparisonPayload(CompressionComparisonEventPayload payload) {
        Map<String, Object> map = new HashMap<>();
        // 空值守卫：载荷为空时返回空映射。
        if (payload == null) {
            return map;
        }
        appendIfPresent(map, "tenantId", payload.getTenantId());
        appendIfPresent(map, "workflowId", payload.getWorkflowId());
        appendIfPresent(map, "snapshotId", payload.getSnapshotId());
        appendIfPresent(map, "rolloutVersion", payload.getRolloutVersion());
        appendIfPresent(map, "primarySource", payload.getPrimarySource());
        appendIfPresent(map, "shadowSource", payload.getShadowSource());
        appendIfPresent(map, "winner", payload.getWinner());
        appendIfPresent(map, "rollbackReason", payload.getRollbackReason());
        appendIfPresent(map, "qualityScore", payload.getQualityScore());
        appendIfPresent(map, "comparisonRecordId", payload.getComparisonRecordId());
        return map;
    }

    /**
     * 当值存在时写入载荷映射。
     */
    private void appendIfPresent(Map<String, Object> map, String key, Object value) {
        // 参数守卫：缺少目标映射或键时不写入，避免污染载荷结构。
        if (map == null || key == null) {
            return;
        }
        // 值判定：仅在值非空时写入，保证事件契约字段语义稳定。
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * 安全转换整数。
     */
    private int safeInt(Integer value) {
        if (value == null) {
            return 0;
        }
        return value;
    }

    private void recordStageMetrics(ContextSnapshotStage stage, Map<String, Object> payload) {
        if (metricsPublisher == null || stage == null) {
            return;
        }
        metricsPublisher.incrementWithTags("context_snapshot_events_total", "stage", stage.name());
        if (payload != null) {
            metricsPublisher.recordSummary("context_snapshot_event_payload_chars", payload.toString().length());
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

