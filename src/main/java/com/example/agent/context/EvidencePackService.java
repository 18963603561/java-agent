package com.example.agent.context;

import com.example.agent.observability.MetricsPublisher;
import com.example.agent.research.ResearchCitation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 证据包服务，负责证据包创建、追加和统计索引维护。
 */
@Service
public class EvidencePackService {

    /**
     * 运行上下文中的证据包键。
     */
    public static final String CONTEXT_EVIDENCE_PACK = "evidencePack";

    private static final Logger log = LoggerFactory.getLogger(EvidencePackService.class);

    private final MetricsPublisher metricsPublisher;
    private final Map<String, EvidencePack> packStore = new ConcurrentHashMap<>();

    public EvidencePackService(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 创建证据包。
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
            pack = getOrCreatePack(tenantId, workflowId, snapshotId);
            if (context != null) {
                context.put(CONTEXT_EVIDENCE_PACK, pack);
            }
        }
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
        return pack;
    }

    /**
     * 按租户与工作流获取或创建证据包。
     */
    public EvidencePack getOrCreatePack(String tenantId, String workflowId, String snapshotId) {
        String key = buildStoreKey(tenantId, workflowId);
        return packStore.compute(key, (ignored, existing) -> {
            EvidencePack pack = existing;
            if (pack == null) {
                pack = createPack(tenantId, workflowId, snapshotId);
            }
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
            return pack;
        });
    }

    /**
     * 按租户与工作流查询证据包。
     */
    public EvidencePack getPack(String tenantId, String workflowId) {
        return packStore.get(buildStoreKey(tenantId, workflowId));
    }

    /**
     * 追加工具证据。
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
     */
    public EvidenceItem appendEvidence(EvidencePack pack,
                                       EvidenceItem item,
                                       String tenantId,
                                       String workflowId) {
        if (pack == null || item == null) {
            return null;
        }
        synchronized (pack) {
            if (item.getEvidenceId() == null || item.getEvidenceId().isBlank()) {
                item.setEvidenceId(buildEvidenceId(item.getType()));
            }
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(Instant.now());
            }
            pack.append(item);
            rebuildIndex(pack);
            pack.recomputeStats();
        }
        metricsPublisher.increment("evidence_pack_append_total");
        log.info("evidence append tenantId={}, workflowId={}, type={}, stepId={}, ref={} ",
                tenantId, workflowId, item.getType(), item.getStepId(), item.getRef());
        return item;
    }

    /**
     * 完成证据包统计。
     */
    public EvidencePack finalizePack(EvidencePack pack, String tenantId, String workflowId) {
        if (pack == null) {
            return null;
        }
        synchronized (pack) {
            rebuildIndex(pack);
            pack.recomputeStats();
        }
        metricsPublisher.increment("evidence_pack_finalize_total");
        log.info("evidence finalize tenantId={}, workflowId={}, totalCount={}, approxChars={}",
                tenantId,
                workflowId,
                pack.getStats() != null ? pack.getStats().getTotalCount() : null,
                pack.getStats() != null ? pack.getStats().getApproxChars() : null);
        return pack;
    }

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
                if (item == null || item.getEvidenceId() == null) {
                    continue;
                }
                String stepId = item.getStepId() == null ? "unknown" : item.getStepId();
                byStepId.computeIfAbsent(stepId, key -> new ArrayList<>()).add(item.getEvidenceId());
                String type = item.getType() == null ? "UNKNOWN" : item.getType().name();
                byType.computeIfAbsent(type, key -> new ArrayList<>()).add(item.getEvidenceId());
            }
        }
        index.setByStepId(byStepId);
        index.setByType(byType);
        pack.setIndex(index);
    }

    private String buildEvidenceId(EvidenceType type) {
        String prefix = type == null ? "unknown" : type.name().toLowerCase();
        return "ev:" + prefix + ":" + UUID.randomUUID();
    }

    private String buildCitationRef(ResearchCitation citation) {
        if (citation == null) {
            return null;
        }
        if (citation.getSource() != null && !citation.getSource().isBlank()) {
            return trimText(citation.getSource(), 180);
        }
        if (citation.getSnippet() != null && !citation.getSnippet().isBlank()) {
            return "snippet:" + Integer.toHexString(citation.getSnippet().hashCode());
        }
        return "citation:" + UUID.randomUUID();
    }

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

    private String buildStoreKey(String tenantId, String workflowId) {
        String tenant = tenantId == null ? "unknown" : tenantId;
        String workflow = workflowId == null ? "unknown" : workflowId;
        return tenant + ":" + workflow;
    }
}
