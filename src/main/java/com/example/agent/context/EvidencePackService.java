package com.example.agent.context;

import com.example.agent.observability.MetricsPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 证据包聚合器，用于创建与追加证据信息并维护统计数据。
 */
@Service
public class EvidencePackService {

    public static final String CONTEXT_EVIDENCE_PACK = "evidencePack";

    private static final Logger log = LoggerFactory.getLogger(EvidencePackService.class);

    private final MetricsPublisher metricsPublisher;

    public EvidencePackService(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 创建证据包并初始化基础元信息。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @return 新证据包
     */
    public EvidencePack createPack(String tenantId, String workflowId, String snapshotId) {
        EvidencePack pack = new EvidencePack();
        pack.setTenantId(tenantId);
        pack.setWorkflowId(workflowId);
        pack.setSnapshotId(snapshotId);
        pack.setCreatedAt(Instant.now());
        return pack;
    }

    /**
     * 从上下文获取或创建证据包，并写回上下文。
     *
     * @param context 运行上下文
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @return 证据包实例
     */
    public EvidencePack getOrCreatePack(Map<String, Object> context,
                                        String tenantId,
                                        String workflowId,
                                        String snapshotId) {
        EvidencePack pack = null;
        if (context != null) {
            Object value = context.get(CONTEXT_EVIDENCE_PACK);
            if (value instanceof EvidencePack evidencePack) {
                pack = evidencePack;
            }
        }
        if (pack == null) {
            pack = createPack(tenantId, workflowId, snapshotId);
            if (context != null) {
                try {
                    context.put(CONTEXT_EVIDENCE_PACK, pack);
                } catch (UnsupportedOperationException ex) {
                    // 忽略不可变上下文的写入失败，避免影响主流程。
                }
            }
        } else {
            if (tenantId != null && (pack.getTenantId() == null || pack.getTenantId().isBlank())) {
                pack.setTenantId(tenantId);
            }
            if (workflowId != null && (pack.getWorkflowId() == null || pack.getWorkflowId().isBlank())) {
                pack.setWorkflowId(workflowId);
            }
            if (snapshotId != null && !snapshotId.isBlank()
                    && (pack.getSnapshotId() == null || pack.getSnapshotId().isBlank())) {
                pack.setSnapshotId(snapshotId);
            }
        }
        return pack;
    }

    /**
     * 追加工具调用证据。
     *
     * @param pack 证据包
     * @param evidence 工具调用证据
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     */
    public void addToolCall(EvidencePack pack,
                            ToolCallEvidence evidence,
                            String tenantId,
                            String workflowId) {
        if (pack == null || evidence == null) {
            return;
        }
        synchronized (pack) {
            if (pack.getToolCalls() == null) {
                pack.setToolCalls(new CopyOnWriteArrayList<>());
            }
            pack.getToolCalls().add(evidence);
        }
        metricsPublisher.increment("evidence_pack_tool_calls_total");
        log.info("evidence tool append tenantId={}, workflowId={}, toolName={}, status={}, durationMs={}",
                tenantId,
                workflowId,
                evidence.getToolName(),
                evidence.getStatus(),
                evidence.getDurationMs());
    }

    /**
     * 追加记忆引用证据列表。
     *
     * @param pack 证据包
     * @param memories 记忆证据列表
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     */
    public void addMemoriesUsed(EvidencePack pack,
                                List<MemoryEvidence> memories,
                                String tenantId,
                                String workflowId) {
        if (pack == null || memories == null || memories.isEmpty()) {
            return;
        }
        synchronized (pack) {
            if (pack.getMemoriesUsed() == null) {
                pack.setMemoriesUsed(new CopyOnWriteArrayList<>());
            }
            pack.getMemoriesUsed().addAll(memories);
        }
        for (int i = 0; i < memories.size(); i++) {
            metricsPublisher.increment("evidence_pack_memories_used_total");
        }
        log.info("evidence memory append tenantId={}, workflowId={}, memoriesAddedCount={}",
                tenantId,
                workflowId,
                memories.size());
    }

    /**
     * 结束聚合并刷新统计数据。
     *
     * @param pack 证据包
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 刷新后的证据包
     */
    public EvidencePack finalizePack(EvidencePack pack, String tenantId, String workflowId) {
        if (pack == null) {
            return null;
        }
        EvidenceStats stats;
        synchronized (pack) {
            stats = pack.recomputeStats();
        }
        metricsPublisher.increment("evidence_pack_finalize_total");
        log.info("evidence finalize tenantId={}, workflowId={}, toolCallsCount={}, memoriesCount={}, citationsCount={}, approxChars={}",
                tenantId,
                workflowId,
                stats != null ? stats.getToolCallsCount() : null,
                stats != null ? stats.getMemoriesCount() : null,
                stats != null ? stats.getCitationsCount() : null,
                stats != null ? stats.getApproxChars() : null);
        return pack;
    }
}
