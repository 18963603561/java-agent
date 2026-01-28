package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆存取服务，负责保存、检索与压缩。
 */
@Service
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);
    private static final List<String> DEFAULT_RETRIEVAL_PRIORITY = List.of("SEMANTIC", "RECENT", "SUMMARY");

    private final MemoryRepository memoryRepository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;
    private final RecentMemoryStore recentMemoryStore;
    private final SemanticMemoryStore semanticMemoryStore;
    private final CompressedMemoryStore compressedMemoryStore;
    private final MemoryPolicy memoryPolicy;
    private final MemoryExpireProperties expireProperties;
    private final MemoryExpirationService expirationService;
    private final Map<String, Instant> cleanupTimestamps = new ConcurrentHashMap<>();

    public MemoryStore(MemoryRepository memoryRepository,
                       ObjectProvider<VectorStore> vectorStoreProvider,
                       ObjectProvider<EmbeddingService> embeddingServiceProvider,
                       RecentMemoryStore recentMemoryStore,
                       SemanticMemoryStore semanticMemoryStore,
                       CompressedMemoryStore compressedMemoryStore,
                       MemoryPolicy memoryPolicy,
                       MemoryExpireProperties expireProperties,
                       MemoryExpirationService expirationService) {
        this.memoryRepository = memoryRepository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.recentMemoryStore = recentMemoryStore;
        this.semanticMemoryStore = semanticMemoryStore;
        this.compressedMemoryStore = compressedMemoryStore;
        this.memoryPolicy = memoryPolicy;
        this.expireProperties = expireProperties;
        this.expirationService = expirationService;
    }

    /**
     * 保存记忆记录。
     *
     * @param record 记忆记录
     * @param tenantContext 租户上下文
     * @return 保存后的记录
     */
    public MemoryRecord save(MemoryRecord record, TenantContext tenantContext) {
        if (record.getMemoryId() == null || record.getMemoryId().isBlank()) {
            record.setMemoryId(UUID.randomUUID().toString());
        }
        record.setTenantId(tenantContext.getTenantId());
        Instant now = Instant.now();
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        if (expirationService != null) {
            expirationService.applyExpiration(record, now);
        }
        MemoryRecord saved = recentMemoryStore.save(record);
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        String text = firstNonBlank(record.getContent(), record.getSummary());
        if (vectorStore != null && StringUtils.hasText(text)) {
            try {
                EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
                if (embeddingService != null) {
                    List<Float> embedding = embeddingService.embed(text);
                    vectorStore.upsert(tenantContext.getTenantId(), record, embedding);
                } else {
                    log.warn("嵌入服务不可用，跳过向量写入, tenantId={}, memoryId={}",
                            tenantContext.getTenantId(), record.getMemoryId());
                }
            } catch (Exception ex) {
                log.error("记忆向量写入失败, tenantId={}, memoryId={}",
                        tenantContext.getTenantId(), record.getMemoryId(), ex);
            }
        }
        log.info("记忆保存, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), record.getSessionId(), record.getMemoryId());
        autoCompressIfNeeded(record.getSessionId(), tenantContext);
        return saved;
    }

    /**
     * 检索记忆。
     *
     * @param query 查询请求
     * @param tenantContext 租户上下文
     * @return 检索结果
     */
    public MemorySearchResult search(MemoryQuery query, TenantContext tenantContext) {
        return search(query, tenantContext, null);
    }

    /**
     * 按策略优先级检索记忆内容。
     *
     * @param query 检索请求
     * @param tenantContext 租户上下文
     * @param retrievalPriority 检索优先级顺序
     * @return 检索结果
     */
    public MemorySearchResult search(MemoryQuery query,
                                     TenantContext tenantContext,
                                     List<String> retrievalPriority) {
        int limit = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 10;
        cleanupExpiredIfNeeded(tenantContext, "search");
        List<MemoryRecord> aggregated = new ArrayList<>();
        if (query != null && StringUtils.hasText(query.getQuery())) {
            List<String> priorityOrder = normalizeRetrievalPriority(retrievalPriority);
            for (String priority : priorityOrder) {
                if (aggregated.size() >= limit) {
                    break;
                }
                switch (priority) {
                    case "RECENT" -> {
                        List<MemoryRecord> recent = recentMemoryStore.search(
                                tenantContext.getTenantId(), query.getSessionId(), query.getQuery(), limit);
                        mergeRecords(aggregated, recent, limit);
                    }
                    case "SEMANTIC" -> {
                        List<MemoryRecord> semantic = semanticMemoryStore.search(query, tenantContext, limit);
                        mergeRecords(aggregated, semantic, limit);
                    }
                    case "SUMMARY" -> {
                        List<MemoryRecord> compressed = compressedMemoryStore.search(
                                tenantContext.getTenantId(), query.getSessionId(), query.getQuery(), limit);
                        mergeRecords(aggregated, compressed, limit);
                    }
                    default -> {
                    }
                }
            }
        }
        autoCompressIfNeeded(query != null ? query.getSessionId() : null, tenantContext);
        List<MemoryRecord> filtered = expirationService != null
                ? expirationService.filterExpired(aggregated, Instant.now())
                : aggregated;
        return new MemorySearchResult(filtered);
    }

    private List<String> normalizeRetrievalPriority(List<String> retrievalPriority) {
        List<String> resolved = new ArrayList<>();
        if (retrievalPriority != null) {
            for (String value : retrievalPriority) {
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
     * 压缩记忆。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩后的记忆记录
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        cleanupExpiredIfNeeded(tenantContext, "compress");
        List<MemoryRecord> records = memoryRepository.findBySession(
                tenantContext.getTenantId(), request.getSessionId());
        MemoryRecord compressed = compressedMemoryStore.compress(
                request.getSessionId(), records, tenantContext, request.getWorkflowId());
        if (compressed == null) {
            log.warn("记忆压缩无效, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), request.getSessionId());
            return null;
        }
        log.info("记忆压缩完成, tenantId={}, sessionId={}",
                tenantContext.getTenantId(), request.getSessionId());
        return compressed;
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

    private void mergeRecords(List<MemoryRecord> target, List<MemoryRecord> source, int limit) {
        if (source == null || source.isEmpty() || target.size() >= limit) {
            return;
        }
        for (MemoryRecord record : source) {
            if (record == null) {
                continue;
            }
            if (target.size() >= limit) {
                break;
            }
            boolean exists = target.stream()
                    .anyMatch(item -> item.getMemoryId() != null && item.getMemoryId().equals(record.getMemoryId()));
            if (!exists) {
                target.add(record);
            }
        }
    }

    private void autoCompressIfNeeded(String sessionId, TenantContext tenantContext) {
        if (tenantContext == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        List<MemoryRecord> records = memoryRepository.findBySession(tenantContext.getTenantId(), sessionId);
        if (memoryPolicy.shouldCompress(records, Instant.now())) {
            MemoryRecord compressed = compressedMemoryStore.compress(sessionId, records, tenantContext);
            if (compressed != null) {
                log.info("自动压缩触发, tenantId={}, sessionId={}, memoryId={}",
                        tenantContext.getTenantId(), sessionId, compressed.getMemoryId());
            }
        }
    }

    private void cleanupExpiredIfNeeded(TenantContext tenantContext, String reason) {
        if (tenantContext == null || expireProperties == null || memoryRepository == null) {
            return;
        }
        if (!expireProperties.isEnabled() || !expireProperties.isCleanupOnRead()) {
            return;
        }
        Instant now = Instant.now();
        String tenantId = tenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        long interval = Math.max(0, expireProperties.getCleanupIntervalSeconds());
        if (interval > 0) {
            // 避免频繁清理导致存储压力过大
            Instant lastCleanup = cleanupTimestamps.get(tenantId);
            if (lastCleanup != null && Duration.between(lastCleanup, now).getSeconds() < interval) {
                return;
            }
        }
        int removed = memoryRepository.deleteExpired(tenantId, now);
        cleanupTimestamps.put(tenantId, now);
        if (removed > 0) {
            log.info("过期记忆清理完成, tenantId={}, removed={}, reason={}",
                    tenantId, removed, reason);
        } else {
            log.debug("过期记忆清理无数据, tenantId={}, reason={}", tenantId, reason);
        }
    }
}
