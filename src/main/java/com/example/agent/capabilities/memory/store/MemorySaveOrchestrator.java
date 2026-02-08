package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.policy.MemoryExpirationService;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.store.RecentMemoryStore;
import com.example.agent.capabilities.memory.vector.EmbeddingService;
import com.example.agent.capabilities.memory.vector.VectorStore;
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
 * 璁板繂淇濆瓨缂栨帓鏈嶅姟锛岃礋璐ｅ啓鍏?recent 灞傚苟鍚屾鍚戦噺绱㈠紩銆? */
@Service
public class MemorySaveOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemorySaveOrchestrator.class);

    /**
     * Recent 灞傚瓨鍌ㄣ€?     */
    private final RecentMemoryStore recentMemoryStore;

    /**
     * 鍚戦噺瀛樺偍鎻愪緵鍣ㄣ€?     */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /**
     * 宓屽叆鏈嶅姟鎻愪緵鍣ㄣ€?     */
    private final ObjectProvider<EmbeddingService> embeddingServiceProvider;

    /**
     * 缁存姢鏈嶅姟銆?     */
    private final MemoryMaintenanceService memoryMaintenanceService;

    /**
     * 杩囨湡澶勭悊鏈嶅姟銆?     */
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
     * 淇濆瓨璁板繂璁板綍銆?     *
     * @param record 璁板繂璁板綍
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @return 淇濆瓨鍚庣殑璁板綍
     */
    public MemoryRecord save(MemoryRecord record, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext)) {
            log.warn("璁板繂淇濆瓨璺宠繃, reason=tenant_invalid");
            return null;
        }
        if (record == null) {
            log.warn("璁板繂淇濆瓨璺宠繃, tenantId={}, reason=record_missing", tenantContext.getTenantId());
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
        log.info("璁板繂淇濆瓨, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), record.getSessionId(), record.getMemoryId());
        memoryMaintenanceService.autoCompressIfNeeded(record.getSessionId(), tenantContext);
        return saved;
    }

    /**
     * 灏濊瘯鍐欏叆鍚戦噺绱㈠紩銆?     */
    private void writeVectorIfPossible(MemoryRecord record, TenantContext tenantContext) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        String text = MemoryTextUtils.firstNonBlank(record.getContent(), record.getSummary());
        if (vectorStore == null || !StringUtils.hasText(text)) {
            return;
        }
        try {
            EmbeddingService embeddingService = embeddingServiceProvider.getIfAvailable();
            if (embeddingService == null) {
                log.warn("宓屽叆鏈嶅姟涓嶅彲鐢紝璺宠繃鍚戦噺鍐欏叆, tenantId={}, memoryId={}",
                        tenantContext.getTenantId(), record.getMemoryId());
                return;
            }
            List<Float> embedding = embeddingService.embed(text);
            vectorStore.upsert(tenantContext.getTenantId(), record, embedding);
        } catch (Exception ex) {
            log.error("璁板繂鍚戦噺鍐欏叆澶辫触, tenantId={}, memoryId={}",
                    tenantContext.getTenantId(), record.getMemoryId(), ex);
        }
    }

    /**
     * 鏍￠獙绉熸埛涓婁笅鏂囨槸鍚︽湁鏁堛€?     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null && StringUtils.hasText(tenantContext.getTenantId());
    }
}

