package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 记忆存取服务，负责保存、搜索与压缩。
 */
@Service
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ConcurrentHashMap<String, List<MemoryRecord>> memoryBySession = new ConcurrentHashMap<>();

    public MemoryStore(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
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
        String key = buildKey(tenantContext.getTenantId(), record.getSessionId());
        memoryBySession.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(record);
        log.info("记忆保存, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), record.getSessionId(), record.getMemoryId());
        return record;
    }

    /**
     * 搜索记忆。
     *
     * @param query 查询请求
     * @param tenantContext 租户上下文
     * @return 搜索结果
     */
    public MemorySearchResult search(MemoryQuery query, TenantContext tenantContext) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        int limit = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 10;
        if (vectorStore == null) {
            log.warn("向量存储不可用, tenantId={}", tenantContext.getTenantId());
            return new MemorySearchResult(List.of());
        }
        List<MemoryRecord> results = vectorStore.search(tenantContext.getTenantId(), query.getQuery(), limit);
        return new MemorySearchResult(results);
    }

    /**
     * 压缩记忆。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩后的记忆记录
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        String key = buildKey(tenantContext.getTenantId(), request.getSessionId());
        List<MemoryRecord> records = memoryBySession.getOrDefault(key, List.of());
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

    private String buildKey(String tenantId, String sessionId) {
        return tenantId + ":" + (sessionId == null ? "-" : sessionId);
    }
}
