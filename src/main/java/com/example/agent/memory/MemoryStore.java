package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆存取服务，负责保存、搜索与压缩。
 */
@Service
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);

    private final MemoryRepository memoryRepository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;

    public MemoryStore(MemoryRepository memoryRepository,
                       ObjectProvider<VectorStore> vectorStoreProvider,
                       ObjectProvider<EmbeddingService> embeddingServiceProvider) {
        this.memoryRepository = memoryRepository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.embeddingServiceProvider = embeddingServiceProvider;
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
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
        MemoryRecord saved = memoryRepository.save(record);
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
        return saved;
    }

    /**
     * 搜索记忆。
     *
     * @param query 查询请求
     * @param tenantContext 租户上下文
     * @return 搜索结果
     */
    public MemorySearchResult search(MemoryQuery query, TenantContext tenantContext) {
        int limit = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 10;
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null && StringUtils.hasText(query.getQuery())) {
            try {
                EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
                if (embeddingService != null) {
                    List<Float> embedding = embeddingService.embed(query.getQuery());
                    List<MemoryRecord> results = vectorStore.search(
                            tenantContext.getTenantId(), query.getSessionId(), embedding, limit);
                    return new MemorySearchResult(results);
                }
                log.warn("嵌入服务不可用，使用文本检索兜底, tenantId={}", tenantContext.getTenantId());
            } catch (Exception ex) {
                log.error("记忆向量检索失败, tenantId={}, sessionId={}",
                        tenantContext.getTenantId(), query.getSessionId(), ex);
            }
        } else {
            log.warn("向量存储不可用，使用文本检索兜底, tenantId={}", tenantContext.getTenantId());
        }
        List<MemoryRecord> fallback = memoryRepository.search(
                tenantContext.getTenantId(), query.getSessionId(), query.getQuery(), limit);
        return new MemorySearchResult(fallback);
    }

    /**
     * 压缩记忆。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩后的记忆记录
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        List<MemoryRecord> records = memoryRepository.findBySession(
                tenantContext.getTenantId(), request.getSessionId());
        StringBuilder summary = new StringBuilder();
        for (MemoryRecord record : records) {
            if (record.getContent() != null) {
                summary.append(record.getContent()).append(" ");
            }
        }
        MemoryRecord compressed = new MemoryRecord();
        compressed.setMemoryId(UUID.randomUUID().toString());
        compressed.setSessionId(request.getSessionId());
        compressed.setSummary(summary.toString().trim());
        compressed.setLayer("compressed");
        compressed.setTenantId(tenantContext.getTenantId());
        compressed.setCreatedAt(Instant.now());
        save(compressed, tenantContext);
        log.info("记忆压缩完成, tenantId={}, sessionId={}", tenantContext.getTenantId(), request.getSessionId());
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
}
