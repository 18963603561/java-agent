package com.example.agent.capabilities.context.evidence;

import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.context.research.ResearchCitation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 证据包服务，负责证据包创建、追加、索引维护与清理。
 */
@Service
public class EvidencePackService {

    private static final Logger log = LoggerFactory.getLogger(EvidencePackService.class);

    private static final long DEFAULT_EXPIRE_MILLIS = TimeUnit.HOURS.toMillis(2);

    private final MetricsPublisher metricsPublisher;
    private final Map<String, EvidencePack> packStore = new ConcurrentHashMap<>();

    /**
     * 证据包过期时长。
     */
    private final long expireMillis;

    /**
     * 构造证据包服务。
     *
     * @param metricsPublisher 指标发布器
     */
    @Autowired
    public EvidencePackService(MetricsPublisher metricsPublisher) {
        this(metricsPublisher, DEFAULT_EXPIRE_MILLIS);
    }

    /**
     * 构造证据包服务（带过期控制）。
     *
     * @param metricsPublisher 指标发布器
     * @param expireMillis 过期毫秒数
     */
    public EvidencePackService(MetricsPublisher metricsPublisher, long expireMillis) {
        this.metricsPublisher = metricsPublisher;
        this.expireMillis = expireMillis;
    }

    /**
     * 创建证据包。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @return 证据包
     */
    public EvidencePack createPack(String tenantId, String workflowId, String snapshotId) {
        EvidencePack pack = new EvidencePack();
        pack.setPackId("ep-" + UUID.randomUUID());
        pack.setTenantId(tenantId);
        pack.setWorkflowId(workflowId);
        pack.setSnapshotId(snapshotId);
        pack.setCreatedAt(Instant.now());
        pack.setEvidences(new ArrayList<>());
        pack.setIndex(new EvidenceIndex());
        return pack;
    }

    /**
     * 从上下文读取或创建证据包。
     *
     * @param context 运行上下文
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @return 证据包
     */
    public EvidencePack getOrCreatePack(Map<String, Object> context,
                                        String tenantId,
                                        String workflowId,
                                        String snapshotId) {
        validateStoreKey(tenantId, workflowId);
        EvidencePack pack = null;
        if (context != null) {
            Object value = context.get(ContextRuntimeKeys.EVIDENCE_PACK);
            if (value instanceof EvidencePack evidencePack) {
                pack = evidencePack;
            }
        }
        if (pack == null) {
            pack = getOrCreatePack(tenantId, workflowId, snapshotId);
            if (context != null) {
                context.put(ContextRuntimeKeys.EVIDENCE_PACK, pack);
            }
        }
        if (StringUtils.hasText(tenantId) && !StringUtils.hasText(pack.getTenantId())) {
            pack.setTenantId(tenantId);
        }
        if (StringUtils.hasText(workflowId) && !StringUtils.hasText(pack.getWorkflowId())) {
            pack.setWorkflowId(workflowId);
        }
        if (StringUtils.hasText(snapshotId) && !StringUtils.hasText(pack.getSnapshotId())) {
            pack.setSnapshotId(snapshotId);
        }
        return pack;
    }

    /**
     * 获取或创建证据包。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @param snapshotId 快照标识
     * @return 证据包
     */
    public EvidencePack getOrCreatePack(String tenantId, String workflowId, String snapshotId) {
        validateStoreKey(tenantId, workflowId);
        cleanupExpiredPacks();
        String key = buildStoreKey(tenantId, workflowId);
        return packStore.compute(key, (ignored, existing) -> {
            EvidencePack pack = existing;
            if (pack == null || isExpired(pack)) {
                pack = createPack(tenantId, workflowId, snapshotId);
            }
            if (StringUtils.hasText(tenantId) && !StringUtils.hasText(pack.getTenantId())) {
                pack.setTenantId(tenantId);
            }
            if (StringUtils.hasText(workflowId) && !StringUtils.hasText(pack.getWorkflowId())) {
                pack.setWorkflowId(workflowId);
            }
            if (StringUtils.hasText(snapshotId) && !StringUtils.hasText(pack.getSnapshotId())) {
                pack.setSnapshotId(snapshotId);
            }
            return pack;
        });
    }

    /**
     * 根据租户与工作流查询证据包。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据包
     */
    public EvidencePack getPack(String tenantId, String workflowId) {
        validateStoreKey(tenantId, workflowId);
        cleanupExpiredPacks();
        EvidencePack pack = packStore.get(buildStoreKey(tenantId, workflowId));
        if (pack != null && isExpired(pack)) {
            packStore.remove(buildStoreKey(tenantId, workflowId));
            if (metricsPublisher != null) {
                metricsPublisher.increment("evidence_pack_expired_total");
            }
            return null;
        }
        return pack;
    }

    /**
     * 删除指定租户与工作流的证据包。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 是否删除成功
     */
    public boolean removePack(String tenantId, String workflowId) {
        validateStoreKey(tenantId, workflowId);
        String key = buildStoreKey(tenantId, workflowId);
        EvidencePack removed = packStore.remove(key);
        boolean success = removed != null;
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags("evidence_pack_remove_total", "removed", String.valueOf(success));
        }
        log.info("evidence remove tenantId={}, workflowId={}, removed={}", tenantId, workflowId, success);
        return success;
    }

    /**
     * 追加工具证据。
     *
     * @param pack 证据包
     * @param stepId 步骤标识
     * @param toolName 工具名
     * @param ref 引用标识
     * @param digest 摘要
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据项
     */
    public EvidenceItem appendToolResult(EvidencePack pack,
                                         String stepId,
                                         String toolName,
                                         String ref,
                                         String digest,
                                         String tenantId,
                                         String workflowId) {
        return appendEvidence(pack, buildItem(EvidenceType.TOOL_RESULT, stepId, toolName, ref, digest),
                tenantId, workflowId);
    }

    /**
     * 追加记忆证据。
     *
     * @param pack 证据包
     * @param stepId 步骤标识
     * @param source 来源
     * @param ref 引用标识
     * @param digest 摘要
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据项
     */
    public EvidenceItem appendMemory(EvidencePack pack,
                                     String stepId,
                                     String source,
                                     String ref,
                                     String digest,
                                     String tenantId,
                                     String workflowId) {
        return appendEvidence(pack, buildItem(EvidenceType.MEMORY, stepId, source, ref, digest),
                tenantId, workflowId);
    }

    /**
     * 追加研究证据。
     *
     * @param pack 证据包
     * @param stepId 步骤标识
     * @param source 来源
     * @param ref 引用标识
     * @param digest 摘要
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据项
     */
    public EvidenceItem appendResearch(EvidencePack pack,
                                       String stepId,
                                       String source,
                                       String ref,
                                       String digest,
                                       String tenantId,
                                       String workflowId) {
        return appendEvidence(pack, buildItem(EvidenceType.RESEARCH, stepId, source, ref, digest),
                tenantId, workflowId);
    }

    /**
     * 追加裁剪证据。
     *
     * @param pack 证据包
     * @param stepId 步骤标识
     * @param source 来源
     * @param ref 引用标识
     * @param digest 摘要
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据项
     */
    public EvidenceItem appendTruncation(EvidencePack pack,
                                         String stepId,
                                         String source,
                                         String ref,
                                         String digest,
                                         String tenantId,
                                         String workflowId) {
        return appendEvidence(pack, buildItem(EvidenceType.CONTEXT_TRUNCATION, stepId, source, ref, digest),
                tenantId, workflowId);
    }

    /**
     * 追加研究引用列表。
     *
     * @param pack 证据包
     * @param stepId 步骤标识
     * @param citations 引用列表
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 已追加证据项
     */
    public List<EvidenceItem> appendResearchCitations(EvidencePack pack,
                                                      String stepId,
                                                      List<ResearchCitation> citations,
                                                      String tenantId,
                                                      String workflowId) {
        List<EvidenceItem> appended = new ArrayList<>();
        if (citations == null || citations.isEmpty()) {
            return appended;
        }
        for (ResearchCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            String source = trimText(citation.getSource(), 96);
            String digest = trimText(citation.getSnippet(), 160);
            String ref = buildCitationRef(citation);
            EvidenceItem item = appendResearch(pack, stepId, source, ref, digest, tenantId, workflowId);
            if (item != null) {
                appended.add(item);
            }
        }
        finalizePack(pack, tenantId, workflowId);
        return appended;
    }

    /**
     * 通用追加证据。
     *
     * @param pack 证据包
     * @param item 证据项
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据项
     */
    public EvidenceItem appendEvidence(EvidencePack pack,
                                       EvidenceItem item,
                                       String tenantId,
                                       String workflowId) {
        if (pack == null || item == null) {
            return null;
        }
        synchronized (pack) {
            if (!StringUtils.hasText(item.getEvidenceId())) {
                item.setEvidenceId(buildEvidenceId(item.getType()));
            }
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(Instant.now());
            }
            pack.append(item);
            rebuildIndex(pack);
            pack.recomputeStats();
        }
        if (metricsPublisher != null) {
            metricsPublisher.increment("evidence_pack_append_total");
        }
        log.info("evidence append tenantId={}, workflowId={}, type={}, stepId={}, ref={}",
                tenantId, workflowId, item.getType(), item.getStepId(), item.getRef());
        return item;
    }

    /**
     * 完成证据包统计。
     *
     * @param pack 证据包
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 证据包
     */
    public EvidencePack finalizePack(EvidencePack pack, String tenantId, String workflowId) {
        if (pack == null) {
            return null;
        }
        synchronized (pack) {
            rebuildIndex(pack);
            pack.recomputeStats();
        }
        if (metricsPublisher != null) {
            metricsPublisher.increment("evidence_pack_finalize_total");
        }
        log.info("evidence finalize tenantId={}, workflowId={}, totalCount={}, approxChars={}",
                tenantId,
                workflowId,
                pack.getStats() != null ? pack.getStats().getTotalCount() : null,
                pack.getStats() != null ? pack.getStats().getApproxChars() : null);
        return pack;
    }

    /**
     * 构建证据项。
     *
     * @param type 证据类型
     * @param stepId 步骤标识
     * @param source 来源
     * @param ref 引用标识
     * @param digest 摘要
     * @return 证据项
     */
    private EvidenceItem buildItem(EvidenceType type,
                                   String stepId,
                                   String source,
                                   String ref,
                                   String digest) {
        EvidenceItem item = new EvidenceItem();
        item.setType(type);
        item.setStepId(stepId);
        item.setSource(trimText(source, 96));
        item.setRef(trimText(ref, 180));
        item.setDigest(trimText(digest, 240));
        return item;
    }

    /**
     * 重建证据索引。
     *
     * @param pack 证据包
     */
    private void rebuildIndex(EvidencePack pack) {
        if (pack == null) {
            return;
        }
        List<EvidenceItem> evidences = pack.getEvidences();
        EvidenceIndex index = new EvidenceIndex();
        Map<String, List<String>> byStepId = new LinkedHashMap<>();
        Map<String, List<String>> byType = new LinkedHashMap<>();
        if (evidences != null) {
            for (EvidenceItem item : evidences) {
                if (item == null || !StringUtils.hasText(item.getEvidenceId())) {
                    continue;
                }
                String stepId = StringUtils.hasText(item.getStepId()) ? item.getStepId() : "unknown";
                byStepId.computeIfAbsent(stepId, key -> new ArrayList<>()).add(item.getEvidenceId());
                String type = item.getType() == null ? "UNKNOWN" : item.getType().name();
                byType.computeIfAbsent(type, key -> new ArrayList<>()).add(item.getEvidenceId());
            }
        }
        index.setByStepId(byStepId);
        index.setByType(byType);
        pack.setIndex(index);
    }

    /**
     * 构建证据标识。
     *
     * @param type 证据类型
     * @return 证据标识
     */
    private String buildEvidenceId(EvidenceType type) {
        String prefix = type == null ? "unknown" : type.name().toLowerCase();
        return "ev:" + prefix + ":" + UUID.randomUUID();
    }

    /**
     * 从研究引用构建引用标识。
     *
     * @param citation 研究引用
     * @return 引用标识
     */
    private String buildCitationRef(ResearchCitation citation) {
        if (citation == null) {
            return null;
        }
        if (StringUtils.hasText(citation.getSource())) {
            return trimText(citation.getSource(), 180);
        }
        if (StringUtils.hasText(citation.getSnippet())) {
            return "snippet:" + Integer.toHexString(citation.getSnippet().hashCode());
        }
        return "citation:" + UUID.randomUUID();
    }

    /**
     * 截断文本到指定长度。
     *
     * @param text 文本
     * @param maxChars 最大长度
     * @return 截断后文本
     */
    private String trimText(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * 构建存储键。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 存储键
     */
    private String buildStoreKey(String tenantId, String workflowId) {
        return tenantId.trim() + ":" + workflowId.trim();
    }

    /**
     * 校验存储键输入。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     */
    private void validateStoreKey(String tenantId, String workflowId) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(workflowId)) {
            throw new IllegalArgumentException("tenantId/workflowId must not be blank");
        }
    }

    /**
     * 判断证据包是否过期。
     *
     * @param pack 证据包
     * @return 是否过期
     */
    private boolean isExpired(EvidencePack pack) {
        if (pack == null || pack.getCreatedAt() == null || expireMillis <= 0) {
            return false;
        }
        long age = Instant.now().toEpochMilli() - pack.getCreatedAt().toEpochMilli();
        return age > expireMillis;
    }

    /**
     * 清理过期证据包。
     */
    private void cleanupExpiredPacks() {
        if (packStore.isEmpty() || expireMillis <= 0) {
            return;
        }
        int removed = 0;
        for (Map.Entry<String, EvidencePack> entry : new ArrayList<>(packStore.entrySet())) {
            if (entry == null || !isExpired(entry.getValue())) {
                continue;
            }
            if (packStore.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        if (removed > 0) {
            if (metricsPublisher != null) {
                metricsPublisher.incrementWithTags("evidence_pack_cleanup_total", "removed", String.valueOf(removed));
            }
            log.info("evidence cleanup removed={} expiredPacks", removed);
        }
    }
}
