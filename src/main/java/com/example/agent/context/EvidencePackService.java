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

    /**
     * 上下文中保存证据包的键名。
     */
    public static final String CONTEXT_EVIDENCE_PACK = "evidencePack";

    private static final Logger log = LoggerFactory.getLogger(EvidencePackService.class);
    /**
     * 引用标签的最大长度，避免提示词膨胀。
     */
    private static final int MAX_CITATION_LABEL_CHARS = 120;
    /**
     * 引用标识的最大长度，避免索引过长。
     */
    private static final int MAX_CITATION_REF_ID_CHARS = 200;
    /**
     * 引用来源字段的最大长度。
     */
    private static final int MAX_CITATION_SOURCE_CHARS = 64;

    /**
     * 指标发布器，用于记录证据包统计信息。
     */
    private final MetricsPublisher metricsPublisher;

    /**
     * 构造证据包服务。
     *
     * @param metricsPublisher 指标发布器
     */
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
        // 写入关联标识，便于回溯
        pack.setTenantId(tenantId);
        pack.setWorkflowId(workflowId);
        pack.setSnapshotId(snapshotId);
        // 使用当前时间作为创建时间
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
        // 先尝试从上下文取已有证据包
        if (context != null) {
            Object value = context.get(CONTEXT_EVIDENCE_PACK);
            if (value instanceof EvidencePack evidencePack) {
                pack = evidencePack;
            }
        }
        if (pack == null) {
            // 没有则创建并尝试写回上下文
            pack = createPack(tenantId, workflowId, snapshotId);
            if (context != null) {
                // 可能是不可变上下文，因此需要捕获写入异常
                try {
                    context.put(CONTEXT_EVIDENCE_PACK, pack);
                } catch (UnsupportedOperationException ex) {
                    // 忽略不可变上下文的写入失败，避免影响主流程。
                }
            }
        } else {
            // 如果已有证据包缺少标识，则补齐
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
        // 证据包可能被多线程访问，写入时加锁
        synchronized (pack) {
            if (pack.getToolCalls() == null) {
                pack.setToolCalls(new CopyOnWriteArrayList<>());
            }
            pack.getToolCalls().add(evidence);
        }
        // 指标与日志仅记录概要，不记录正文
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
        // 证据包可能被多线程访问，写入时加锁
        synchronized (pack) {
            if (pack.getMemoriesUsed() == null) {
                pack.setMemoriesUsed(new CopyOnWriteArrayList<>());
            }
            pack.getMemoriesUsed().addAll(memories);
        }
        // 按数量累计指标
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
    /**
     * 追加研究引用证据并刷新统计信息。
     *
     * @param pack 证据包
     * @param citations 研究引用列表
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param stage 引用来源阶段
     */
    public void addResearchCitations(EvidencePack pack,
                                     List<com.example.agent.research.ResearchCitation> citations,
                                     String tenantId,
                                     String workflowId,
                                     String stage) {
        if (citations == null || citations.isEmpty()) {
            return;
        }
        List<Citation> mapped = mapResearchCitations(citations);
        addCitations(pack, mapped, tenantId, workflowId, stage);
    }

    /**
     * 追加引用证据并刷新统计信息。
     *
     * @param pack 证据包
     * @param citations 引用列表
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param stage 引用来源阶段
     */
    public void addCitations(EvidencePack pack,
                             List<Citation> citations,
                             String tenantId,
                             String workflowId,
                             String stage) {
        if (pack == null || citations == null || citations.isEmpty()) {
            return;
        }
        // 写入引用列表并保证线程安全
        synchronized (pack) {
            if (pack.getCitations() == null) {
                pack.setCitations(new CopyOnWriteArrayList<>());
            }
            pack.getCitations().addAll(citations);
        }
        int addedCount = citations.size();
        // 累计新增引用数量
        for (int i = 0; i < addedCount; i++) {
            metricsPublisher.increment("evidence_pack_citations_added_total");
        }
        // 重新计算统计，确保数量一致
        EvidenceStats stats = pack.recomputeStats();
        int totalCount = stats != null && stats.getCitationsCount() != null ? stats.getCitationsCount() : 0;
        // 阶段信息用于多阶段归因
        String stageTag = stage == null || stage.isBlank() ? "unknown" : stage;
        metricsPublisher.incrementWithTags("evidence_pack_citations_total", totalCount, "stage", stageTag);
        log.info("evidence citations append tenantId={}, workflowId={}, citationsAddedCount={}, citationsTotalCount={}",
                tenantId,
                workflowId,
                addedCount,
                totalCount);
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
        // 统计计算需要在锁内完成，避免并发修改
        synchronized (pack) {
            stats = pack.recomputeStats();
        }
        // 指标与日志只输出统计摘要
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

    /**
     * 将研究引用映射为证据引用，并做截断处理。
     */
    private List<Citation> mapResearchCitations(List<com.example.agent.research.ResearchCitation> citations) {
        List<Citation> mapped = new java.util.ArrayList<>();
        for (com.example.agent.research.ResearchCitation research : citations) {
            if (research == null) {
                continue;
            }
            // 统一截断，避免过长字段污染提示词
            String source = trimText(research.getSource(), MAX_CITATION_SOURCE_CHARS);
            String label = trimText(research.getSnippet(), MAX_CITATION_LABEL_CHARS);
            // 构造稳定引用标识，便于去重
            String refId = buildRefId(source, label);
            Citation citation = new Citation();
            citation.setType("RESEARCH");
            citation.setSource(source);
            citation.setRefId(refId);
            if (label != null && !label.isBlank()) {
                citation.setLabel(label);
            }
            // 保留原始抓取时间
            citation.setFetchedAt(research.getFetchedAt());
            mapped.add(citation);
        }
        return mapped;
    }

    private String buildRefId(String source, String label) {
        // 优先使用可读网址作为引用标识
        if (source != null && (source.startsWith("http://") || source.startsWith("https://"))) {
            return trimText(source, MAX_CITATION_REF_ID_CHARS);
        }
        // 不满足网址时用来源加摘要哈希拼接
        String base = source != null && !source.isBlank() ? source : "research";
        String hash = label != null && !label.isBlank() ? Integer.toHexString(label.hashCode()) : "unknown";
        return trimText(base + ":" + hash, MAX_CITATION_REF_ID_CHARS);
    }

    private String trimText(String text, int maxChars) {
        // 统一处理空值与空白
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        // 最大长度小于等于零时不做截断
        if (maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }
        // 超长直接截断
        return trimmed.substring(0, maxChars);
    }
}
