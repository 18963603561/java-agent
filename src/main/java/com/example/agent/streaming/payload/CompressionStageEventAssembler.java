package com.example.agent.streaming.payload;

import com.example.agent.capabilities.context.compression.contract.ContextCompressionResult;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import org.springframework.stereotype.Component;

/**
 * 压缩阶段事件载荷装配器。
 *
 * <p>用途：集中封装压缩阶段事件字段组装，避免发布器重复拼装载荷。</p>
 */
@Component
public class CompressionStageEventAssembler {

    /**
     * 组装压缩阶段事件载荷。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @param stage 上下文阶段
     * @param snapshot 上下文快照
     * @param compressionSummary 压缩摘要
     * @param evidenceStats 证据统计摘要
     * @return 压缩阶段事件载荷
     */
    public CompressionStageEventPayload assemble(String tenantId,
                                                 String workflowId,
                                                 String snapshotId,
                                                 ContextSnapshotStage stage,
                                                 ContextSnapshot snapshot,
                                                 ContextCompressionSummary compressionSummary,
                                                 EvidenceStatsSummaryView evidenceStats) {
        CompressionStageEventPayload payload = new CompressionStageEventPayload();
        payload.setTenantId(tenantId);
        payload.setWorkflowId(workflowId);
        payload.setSnapshotId(snapshotId);
        payload.setStage(stage);
        payload.setCompressionSummary(compressionSummary);
        // 证据兜底：优先使用显式统计，缺失时回退快照派生统计。
        EvidenceStatsSummaryView resolved = evidenceStats != null ? evidenceStats : fromSnapshot(snapshot);
        // 字段写入：证据统计存在时写入载荷，确保订阅端字段完整。
        if (resolved != null) {
            payload.setEvidencePackPresent(resolved.present());
            payload.setEvidenceToolCount(resolved.toolCount());
            payload.setEvidenceMemoryCount(resolved.memoryCount());
            payload.setEvidenceResearchCount(resolved.researchCount());
            payload.setEvidenceTruncationCount(resolved.truncationCount());
            payload.setEvidenceApproxChars(resolved.approxChars());
            payload.setEvidencePackVersion(resolved.version());
        }
        // 结果返回：输出压缩阶段事件载荷供发布器直接发送。
        return payload;
    }

    /**
     * 将压缩结果转换为压缩摘要。
     */
    public ContextCompressionSummary buildCompressionSummary(ContextCompressionResult result) {
        // 结果守卫：仅在压缩触发时生成摘要契约。
        if (result == null || !result.isTriggered()) {
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
        // 结果返回：输出压缩摘要供阶段事件契约复用。
        return summary;
    }

    /**
     * 从快照构建证据摘要视图。
     */
    private EvidenceStatsSummaryView fromSnapshot(ContextSnapshot snapshot) {
        // 快照守卫：快照缺失时输出空统计视图。
        if (snapshot == null || snapshot.getWorkingMemory() == null || snapshot.getWorkingMemory().getEvidencePack() == null) {
            return new EvidenceStatsSummaryView(false, null, 0, 0, 0, 0, 0);
        }
        if (snapshot.getWorkingMemory().getEvidencePack().getStats() == null) {
            return new EvidenceStatsSummaryView(true,
                    snapshot.getWorkingMemory().getEvidencePack().getVersion(),
                    0,
                    0,
                    0,
                    0,
                    0);
        }
        // 统计映射：将证据统计实体映射为只读视图，供事件装配复用。
        return new EvidenceStatsSummaryView(
                true,
                snapshot.getWorkingMemory().getEvidencePack().getVersion(),
                snapshot.getWorkingMemory().getEvidencePack().getStats().getToolCount(),
                snapshot.getWorkingMemory().getEvidencePack().getStats().getMemoryCount(),
                snapshot.getWorkingMemory().getEvidencePack().getStats().getResearchCount(),
                snapshot.getWorkingMemory().getEvidencePack().getStats().getTruncationCount(),
                snapshot.getWorkingMemory().getEvidencePack().getStats().getApproxChars());
    }

    /**
     * 证据统计只读视图。
     */
    public record EvidenceStatsSummaryView(boolean present,
                                           String version,
                                           int toolCount,
                                           int memoryCount,
                                           int researchCount,
                                           int truncationCount,
                                           int approxChars) {
    }
}

