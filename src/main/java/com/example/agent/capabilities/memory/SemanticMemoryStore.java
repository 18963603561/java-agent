package com.example.agent.capabilities.memory;

import com.example.agent.security.auth.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 语义记忆存取层，基于向量检索进行语义搜索。
 */
@Service
public class SemanticMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(SemanticMemoryStore.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;

    public SemanticMemoryStore(ObjectProvider<VectorStore> vectorStoreProvider,
                               ObjectProvider<EmbeddingService> embeddingServiceProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.embeddingServiceProvider = embeddingServiceProvider;
    }

    /**
     * 执行语义检索。
     *
     * @param query 记忆查询
     * @param tenantContext 租户上下文
     * @param limit 返回数量
     * @return 检索结果
     */
    public List<MemoryRecord> search(MemoryQuery query, TenantContext tenantContext, int limit) {
        if (query == null || !StringUtils.hasText(query.getQuery())) {
            return List.of();
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
        if (vectorStore == null || embeddingService == null) {
            return List.of();
        }
        try {
            List<Float> embedding = embeddingService.embed(query.getQuery());
            List<MemoryRecord> results = vectorStore.search(
                    tenantContext.getTenantId(), query.getSessionId(), embedding, limit);
            return filterNonCompressed(results);
        } catch (Exception ex) {
            log.error("语义检索失败, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), query.getSessionId(), ex);
            return List.of();
        }
    }

    private List<MemoryRecord> filterNonCompressed(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            String layer = record.getLayer();
            if (!StringUtils.hasText(layer) || !"compressed".equalsIgnoreCase(layer)) {
                filtered.add(record);
            }
        }
        return filtered;
    }
}
