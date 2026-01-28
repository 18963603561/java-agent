package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.EvidencePackService;
import com.example.agent.context.MemoryEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆召回服务，负责在任务执行前召回会话相关记忆并生成上下文摘要。
 */
@Service
public class MemoryRecallService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRecallService.class);

    private static final String CONTEXT_RECALL_ENABLED = "memoryRecallEnabled";
    private static final String CONTEXT_RECALL_FORCE = "memoryRecallForce";
    private static final String CONTEXT_RECALL_LIMIT = "memoryRecallLimit";
    private static final String CONTEXT_RECALL_MIN_QUERY_LENGTH = "memoryRecallMinQueryLength";
    private static final String CONTEXT_RECALL_INCLUDE_COMPRESSED = "memoryRecallIncludeCompressed";
    private static final String CONTEXT_RECALL_MAX_SUMMARY_CHARS = "memoryRecallMaxSummaryChars";
    private static final String CONTEXT_RECALL_MAX_RECORD_CHARS = "memoryRecallMaxRecordChars";

    /**
     * 记忆存取服务。
     */
    private final MemoryStore memoryStore;

    /**
     * 记忆召回配置。
     */
    private final MemoryRecallProperties properties;

    /**
     * 证据包聚合器。
     */
    private final EvidencePackService evidencePackService;

    public MemoryRecallService(MemoryStore memoryStore,
                               MemoryRecallProperties properties,
                               EvidencePackService evidencePackService) {
        this.memoryStore = memoryStore;
        this.properties = properties;
        this.evidencePackService = evidencePackService;
    }

    /**
     * 基于任务请求与上下文执行记忆召回。
     *
     * @param request 任务请求
     * @param context 上下文覆盖参数
     * @param tenantContext 租户上下文
     * @return 召回结果
     */
    public MemoryRecallResult recall(TaskRequest request, Map<String, Object> context, TenantContext tenantContext) {
        if (tenantContext == null) {
            return MemoryRecallResult.skipped("tenant_missing");
        }
        Map<String, Object> effectiveContext = context != null
                ? context
                : request != null ? request.getContext() : null;

        boolean enabled = resolveBoolean(effectiveContext, CONTEXT_RECALL_ENABLED, properties.isEnabled());
        if (!enabled) {
            log.debug("记忆召回关闭, tenantId={}", tenantContext.getTenantId());
            return MemoryRecallResult.skipped("disabled");
        }
        if (request == null) {
            return MemoryRecallResult.skipped("request_missing");
        }
        String sessionId = request.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            return MemoryRecallResult.skipped("session_missing");
        }
        String query = request.getQuery();
        if (!StringUtils.hasText(query)) {
            return MemoryRecallResult.skipped("query_empty");
        }

        int minQueryLength = resolveInt(effectiveContext, CONTEXT_RECALL_MIN_QUERY_LENGTH,
                properties.getMinQueryLength());
        boolean force = resolveBoolean(effectiveContext, CONTEXT_RECALL_FORCE, false);
        if (!force && query.trim().length() < Math.max(0, minQueryLength)) {
            return MemoryRecallResult.skipped("query_too_short");
        }

        int limit = resolveInt(effectiveContext, CONTEXT_RECALL_LIMIT, properties.getLimit());
        int maxSummaryChars = resolveInt(effectiveContext, CONTEXT_RECALL_MAX_SUMMARY_CHARS,
                properties.getMaxSummaryChars());
        int maxRecordChars = resolveInt(effectiveContext, CONTEXT_RECALL_MAX_RECORD_CHARS,
                properties.getMaxRecordChars());
        boolean includeCompressed = resolveBoolean(effectiveContext, CONTEXT_RECALL_INCLUDE_COMPRESSED,
                properties.isIncludeCompressed());

        log.info("记忆召回开始, tenantId={}, sessionId={}, queryLength={}, limit={}",
                tenantContext.getTenantId(), sessionId, query.length(), limit);

        try {
            MemoryQuery memoryQuery = new MemoryQuery();
            memoryQuery.setSessionId(sessionId);
            memoryQuery.setQuery(query);
            memoryQuery.setLimit(limit);

            MemorySearchResult searchResult = memoryStore.search(memoryQuery, tenantContext);
            List<MemoryRecord> records = searchResult != null ? searchResult.getRecords() : List.of();
            records = filterCompressed(records, includeCompressed);
            if (records.isEmpty()) {
                log.info("记忆召回无命中, tenantId={}, sessionId={}", tenantContext.getTenantId(), sessionId);
                return MemoryRecallResult.skipped("empty");
            }
            List<MemoryRecord> trimmed = trimRecords(records, maxRecordChars);
            String summary = buildSummary(trimmed, maxSummaryChars);
            appendMemoryEvidence(effectiveContext, tenantContext, trimmed);
            log.info("记忆召回完成, tenantId={}, sessionId={}, count={}, summaryLength={}",
                    tenantContext.getTenantId(), sessionId, trimmed.size(),
                    summary == null ? 0 : summary.length());
            return MemoryRecallResult.hit(trimmed, summary);
        } catch (Exception ex) {
            log.error("记忆召回异常, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), sessionId, ex);
            return MemoryRecallResult.skipped("recall_failed");
        }
    }

    private boolean resolveBoolean(Map<String, Object> context, String key, boolean defaultValue) {
        if (context == null || !context.containsKey(key)) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }

    private int resolveInt(Map<String, Object> context, String key, int defaultValue) {
        if (context == null || !context.containsKey(key)) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<MemoryRecord> trimRecords(List<MemoryRecord> records, int maxChars) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        if (maxChars <= 0) {
            return new ArrayList<>(records);
        }
        List<MemoryRecord> trimmed = new ArrayList<>(records.size());
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            MemoryRecord copy = new MemoryRecord();
            copy.setMemoryId(record.getMemoryId());
            copy.setSessionId(record.getSessionId());
            copy.setTaskId(record.getTaskId());
            copy.setTenantId(record.getTenantId());
            copy.setLayer(record.getLayer());
            copy.setCreatedAt(record.getCreatedAt());
            copy.setExpiresAt(record.getExpiresAt());
            copy.setConversationSummary(record.getConversationSummary());
            copy.setWorkingMemorySummary(record.getWorkingMemorySummary());
            copy.setContent(trimText(record.getContent(), maxChars));
            copy.setSummary(trimText(record.getSummary(), maxChars));
            trimmed.add(copy);
        }
        return trimmed;
    }

    private List<MemoryRecord> filterCompressed(List<MemoryRecord> records, boolean includeCompressed) {
        if (includeCompressed || records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if (!"compressed".equalsIgnoreCase(record.getLayer())) {
                filtered.add(record);
            }
        }
        return filtered;
    }

    private String buildSummary(List<MemoryRecord> records, int maxSummaryChars) {
        if (records == null || records.isEmpty() || maxSummaryChars <= 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (MemoryRecord record : records) {
            String text = firstNonBlank(record.getSummary(), record.getContent());
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(index).append(". ").append(text.trim());
            if (builder.length() >= maxSummaryChars) {
                builder.setLength(Math.min(builder.length(), maxSummaryChars));
                break;
            }
            index++;
        }
        String summary = builder.toString().trim();
        return summary.isEmpty() ? null : summary;
    }

    /**
     * 追加记忆证据到证据包，避免影响主流程。
     */
    private void appendMemoryEvidence(Map<String, Object> context,
                                      TenantContext tenantContext,
                                      List<MemoryRecord> records) {
        if (evidencePackService == null || context == null || records == null || records.isEmpty()) {
            return;
        }
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String workflowId = readString(context, "workflowId");
        String snapshotId = readString(context, "snapshotId");
        EvidencePack pack = evidencePackService.getOrCreatePack(context, tenantId, workflowId, snapshotId);
        List<MemoryEvidence> memories = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            MemoryEvidence evidence = new MemoryEvidence();
            evidence.setMemoryId(record.getMemoryId());
            evidence.setExpiresAt(record.getExpiresAt());
            evidence.setSummaryVersion(resolveSummaryVersion(record));
            memories.add(evidence);
        }
        evidencePackService.addMemoriesUsed(pack, memories, tenantId, workflowId);
        evidencePackService.finalizePack(pack, tenantId, workflowId);
    }

    private String resolveSummaryVersion(MemoryRecord record) {
        if (record == null) {
            return null;
        }
        String workingVersion = record.getWorkingMemorySummary() != null
                ? record.getWorkingMemorySummary().getVersion()
                : null;
        String conversationVersion = record.getConversationSummary() != null
                ? record.getConversationSummary().getVersion()
                : null;
        return firstNonBlank(workingVersion, conversationVersion);
    }

    private String readString(Map<String, Object> context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return null;
    }

    private String trimText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }
}
