package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.EmbeddingService;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.RecentMemoryStore;
import com.example.agent.capabilities.memory.VectorStore;
import com.example.agent.capabilities.memory.support.MemoryTextUtils;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆保存编排服务，负责写入 recent 层并同步向量索引。
 */
@Service
public class MemorySaveOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemorySaveOrchestrator.class);

    /**
     * Recent 层存储。
     */
    private final RecentMemoryStore recentMemoryStore;

    /**
     * 向量存储提供器。
     */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /**
     * 嵌入服务提供器。
     */
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;

    /**
     * 维护服务。
     */
    private final MemoryMaintenanceService memoryMaintenanceService;

    /**
     * 过期处理服务。
     */
    private final MemoryExpirationService memoryExpirationService;

    public MemorySaveOrchestrator(RecentMemoryStore recentMemoryStore,
                                  ObjectProvider<VectorStore> vectorStoreProvider,
                                  ObjectProvider<EmbeddingService> embeddingServiceProvider,
                                  MemoryExpirationService memoryExpirationService,
                                  MemoryMaintenanceService memoryMaintenanceService) {
        this.recentMemoryStore = recentMemoryStore;
        this.vectorStoreProvider = vectorStoreProvider;
        this.embeddingServiceProvider = embeddingServiceProvider;
        this.memoryExpirationService = memoryExpirationService;
        this.memoryMaintenanceService = memoryMaintenanceService;
    }

    /**
     * 保存记忆记录。
     *
     * @param record 记忆记录
     * @param tenantContext 租户上下文
     * @return 保存后的记录
     */
    public MemoryRecord save(MemoryRecord record, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext)) {
            log.warn("记忆保存跳过, reason=tenant_invalid");
            return null;
        }
        if (record == null) {
            log.warn("记忆保存跳过, tenantId={}, reason=record_missing", tenantContext.getTenantId());
            return null;
        }
        if (!StringUtils.hasText(record.getMemoryId())) {
            record.setMemoryId(UUID.randomUUID().toString());
        }
        record.setTenantId(tenantContext.getTenantId());
        Instant now = Instant.now();
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        if (memoryExpirationService != null) {
            memoryExpirationService.applyExpiration(record, now);
        }

        MemoryRecord saved = recentMemoryStore.save(record);
        writeVectorIfPossible(record, tenantContext);
        log.info("记忆保存, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), record.getSessionId(), record.getMemoryId());
        memoryMaintenanceService.autoCompressIfNeeded(record.getSessionId(), tenantContext);
        return saved;
    }

    /**
     * 尝试写入向量索引。
     */
    private void writeVectorIfPossible(MemoryRecord record, TenantContext tenantContext) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        String text = MemoryTextUtils.firstNonBlank(record.getContent(), record.getSummary());
        if (vectorStore == null || !StringUtils.hasText(text)) {
            return;
        }
        try {
            EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
            if (embeddingService == null) {
                log.warn("嵌入服务不可用，跳过向量写入, tenantId={}, memoryId={}",
                        tenantContext.getTenantId(), record.getMemoryId());
                return;
            }
            List<Float> embedding = embeddingService.embed(text);
            vectorStore.upsert(tenantContext.getTenantId(), record, embedding);
        } catch (Exception ex) {
            log.error("记忆向量写入失败, tenantId={}, memoryId={}",
                    tenantContext.getTenantId(), record.getMemoryId(), ex);
        }
    }

    /**
     * 校验租户上下文是否有效。
     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null && StringUtils.hasText(tenantContext.getTenantId());
    }
}
