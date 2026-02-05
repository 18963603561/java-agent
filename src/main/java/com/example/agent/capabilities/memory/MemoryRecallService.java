package com.example.agent.capabilities.memory;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String CONTEXT_POLICY_KEY = "contextPolicy";
    private static final String CONTEXT_POLICY_FALLBACK_KEY = "policy";
    private static final List<String> DEFAULT_RETRIEVAL_PRIORITY = List.of("SEMANTIC", "RECENT", "SUMMARY");

    /**
     * 记忆存取服务。
     */
    private final MemoryStore memoryStore;

    /**
     * 记忆召回配置。
     */
    private final MemoryRecallProperties properties;

    /**
     * 脱敏服务。
     */
    private final RedactionService redactionService;

    /**
     * 指标发布器，用于记录策略使用情况。
     */
    private final MetricsPublisher metricsPublisher;

    public MemoryRecallService(MemoryStore memoryStore,
                               MemoryRecallProperties properties,
                               RedactionService redactionService,
                               MetricsPublisher metricsPublisher) {
        this.memoryStore = memoryStore;
        this.properties = properties;
        this.redactionService = redactionService;
        this.metricsPublisher = metricsPublisher;
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
        String workflowId = readString(effectiveContext, "workflowId");

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

        ContextPolicy policy = resolvePolicyFromContext(effectiveContext);
        List<String> retrievalPriority = resolveRetrievalPriority(policy);
        boolean enableSensitiveMask = resolveSensitiveMask(policy);
        if (metricsPublisher != null) {
            metricsPublisher.incrementWithTags("context_retrieval_priority_used_total",
                    "priorityName", formatPriorityTag(retrievalPriority));
        }
        log.info("记忆召回开始, tenantId={}, workflowId={}, sessionId={}, queryLength={}, limit={}, retrievalPriority={}, enableSensitiveMask={}",
                tenantContext.getTenantId(), workflowId, sessionId, query.length(), limit,
                retrievalPriority, enableSensitiveMask);

        try {
            MemoryQuery memoryQuery = new MemoryQuery();
            memoryQuery.setSessionId(sessionId);
            memoryQuery.setQuery(query);
            memoryQuery.setLimit(limit);

            MemorySearchResult searchResult = memoryStore.search(memoryQuery, tenantContext, retrievalPriority);
            List<MemoryRecord> records = searchResult != null ? searchResult.getRecords() : List.of();
            records = filterCompressed(records, includeCompressed);
            if (records.isEmpty()) {
                log.info("记忆召回无命中, tenantId={}, sessionId={}", tenantContext.getTenantId(), sessionId);
                return MemoryRecallResult.skipped("empty");
            }
            List<MemoryRecord> trimmed = trimRecords(records, maxRecordChars);
            int redactionsAppliedCount = applyRedactionToRecords(trimmed, enableSensitiveMask);
            String summary = buildSummary(trimmed, maxSummaryChars);
            RedactionResult summaryRedaction = applyRedactionToSummary(summary, enableSensitiveMask);
            summary = summaryRedaction.getRedactedText();
            redactionsAppliedCount += summaryRedaction.getRedactedCount();
            log.info("记忆召回完成, tenantId={}, workflowId={}, sessionId={}, count={}, summaryLength={}, "
                            + "redactionsAppliedCount={}, enabled={}, rejectOnSecrets={}, redactOnPii={}",
                    tenantContext.getTenantId(), workflowId, sessionId, trimmed.size(),
                    summary == null ? 0 : summary.length(),
                    redactionsAppliedCount,
                    redactionService != null && redactionService.isEnabled(),
                    redactionService != null && redactionService.isRejectOnSecrets(),
                    redactionService != null && redactionService.isRedactOnPii());
            return MemoryRecallResult.hit(trimmed, summary, redactionsAppliedCount);
        } catch (Exception ex) {
            log.error("记忆召回异常, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), sessionId, ex);
            return MemoryRecallResult.skipped("recall_failed");
        }
    }

    private int applyRedactionToRecords(List<MemoryRecord> records, boolean enableSensitiveMask) {
        if (!enableSensitiveMask || redactionService == null || records == null || records.isEmpty()) {
            return 0;
        }
        int redactedCount = 0;
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            RedactionResult contentResult = redactionService.apply(
                    record.getContent(), RedactionStage.RECALL, "memoryContent");
            record.setContent(contentResult.getRedactedText());
            redactedCount += contentResult.getRedactedCount();

            RedactionResult summaryResult = redactionService.apply(
                    record.getSummary(), RedactionStage.RECALL, "memorySummary");
            record.setSummary(summaryResult.getRedactedText());
            redactedCount += summaryResult.getRedactedCount();
        }
        return redactedCount;
    }

    private RedactionResult applyRedactionToSummary(String summary, boolean enableSensitiveMask) {
        if (!enableSensitiveMask || redactionService == null) {
            RedactionResult result = new RedactionResult();
            result.setRedactedText(summary);
            return result;
        }
        return redactionService.apply(summary, RedactionStage.RECALL, "memorySummaryAggregate");
    }

    /**
     * 从上下文解析策略信息，保持向后兼容。
     */
    private ContextPolicy resolvePolicyFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(CONTEXT_POLICY_KEY);
        if (value == null) {
            value = context.get(CONTEXT_POLICY_FALLBACK_KEY);
        }
        if (value instanceof ContextPolicy policy) {
            return policy;
        }
        if (value instanceof Map<?, ?> map) {
            ContextPolicy policy = buildPolicyFromMap(map);
            return hasPolicyContent(policy) ? policy : null;
        }
        return null;
    }

    private ContextPolicy buildPolicyFromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        ContextPolicy policy = new ContextPolicy();
        policy.setPolicyId(readString(map, "policyId"));
        policy.setRetrievalPriority(readStringList(map.get("retrievalPriority")));
        policy.setPruneOrder(readStringList(map.get("pruneOrder")));
        policy.setMaxEvidenceCount(readInteger(map, "maxEvidenceCount"));
        policy.setMaxMemoryCount(readInteger(map, "maxMemoryCount"));
        policy.setEnableSensitiveMask(readBoolean(map.get("enableSensitiveMask")));
        return policy;
    }

    private boolean hasPolicyContent(ContextPolicy policy) {
        if (policy == null) {
            return false;
        }
        return StringUtils.hasText(policy.getPolicyId())
                || (policy.getRetrievalPriority() != null && !policy.getRetrievalPriority().isEmpty())
                || (policy.getPruneOrder() != null && !policy.getPruneOrder().isEmpty())
                || policy.getMaxEvidenceCount() != null
                || policy.getMaxMemoryCount() != null
                || policy.getEnableSensitiveMask() != null;
    }

    /**
     * 解析检索优先级并补齐默认顺序。
     */
    private List<String> resolveRetrievalPriority(ContextPolicy policy) {
        List<String> resolved = new ArrayList<>();
        List<String> configured = policy != null ? policy.getRetrievalPriority() : null;
        if (configured != null) {
            for (String value : configured) {
                String normalized = normalizePriorityValue(value);
                if (normalized != null && !resolved.contains(normalized)) {
                    resolved.add(normalized);
                }
            }
        }
        for (String value : DEFAULT_RETRIEVAL_PRIORITY) {
            if (!resolved.contains(value)) {
                resolved.add(value);
            }
        }
        return resolved;
    }

    private String normalizePriorityValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "RECENT", "SEMANTIC", "SUMMARY" -> upper;
            default -> null;
        };
    }

    /**
     * 解析敏感遮罩开关，空值回退到默认行为。
     */
    private boolean resolveSensitiveMask(ContextPolicy policy) {
        if (policy == null || policy.getEnableSensitiveMask() == null) {
            return true;
        }
        return Boolean.TRUE.equals(policy.getEnableSensitiveMask());
    }

    private String formatPriorityTag(List<String> retrievalPriority) {
        if (retrievalPriority == null || retrievalPriority.isEmpty()) {
            return "default";
        }
        List<String> tags = new ArrayList<>();
        for (String value : retrievalPriority) {
            if (StringUtils.hasText(value)) {
                tags.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return tags.isEmpty() ? "default" : String.join(">", tags);
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

    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    result.add(item.toString());
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return null;
    }

    private Integer readInteger(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private String readString(Map<?, ?> context, String key) {
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
