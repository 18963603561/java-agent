package com.example.agent.capabilities.memory;

import com.example.agent.capabilities.memory.store.MemoryMaintenanceService;
import com.example.agent.capabilities.memory.store.MemorySaveOrchestrator;
import com.example.agent.capabilities.memory.store.MemorySearchOrchestrator;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 记忆存取门面，负责参数入口校验与委派。
 */
@Service
public class MemoryStore {

    /**
     * 保存编排服务。
     */
    private final MemorySaveOrchestrator memorySaveOrchestrator;

    /**
     * 检索编排服务。
     */
    private final MemorySearchOrchestrator memorySearchOrchestrator;

    /**
     * 维护服务。
     */
    private final MemoryMaintenanceService memoryMaintenanceService;

    /**
     * 兼容测试场景时复用的维护服务。
     */
    private static MemoryMaintenanceService createMaintenanceService(MemoryRepository memoryRepository,
                                                                     CompressedMemoryStore compressedMemoryStore,
                                                                     MemoryPolicy memoryPolicy,
                                                                     MemoryExpireProperties expireProperties,
                                                                     MemoryExpirationService expirationService) {
        return new MemoryMaintenanceService(memoryRepository, compressedMemoryStore,
                memoryPolicy, expireProperties, expirationService);
    }

    @Autowired
    public MemoryStore(MemorySaveOrchestrator memorySaveOrchestrator,
                       MemorySearchOrchestrator memorySearchOrchestrator,
                       MemoryMaintenanceService memoryMaintenanceService) {
        this.memorySaveOrchestrator = memorySaveOrchestrator;
        this.memorySearchOrchestrator = memorySearchOrchestrator;
        this.memoryMaintenanceService = memoryMaintenanceService;
    }

    /**
     * 便捷构造方法，用于测试场景快速组装 MemoryStore。
     */
    public MemoryStore(MemoryRepository memoryRepository,
                       ObjectProvider<VectorStore> vectorStoreProvider,
                       ObjectProvider<EmbeddingService> embeddingServiceProvider,
                       RecentMemoryStore recentMemoryStore,
                       SemanticMemoryStore semanticMemoryStore,
                       CompressedMemoryStore compressedMemoryStore,
                       MemoryPolicy memoryPolicy,
                       MemoryExpireProperties expireProperties,
                       MemoryExpirationService expirationService) {
        MemoryMaintenanceService maintenanceService = createMaintenanceService(memoryRepository,
                compressedMemoryStore, memoryPolicy, expireProperties, expirationService);
        this.memorySaveOrchestrator = new MemorySaveOrchestrator(
                recentMemoryStore,
                vectorStoreProvider,
                embeddingServiceProvider,
                expirationService,
                maintenanceService);
        this.memorySearchOrchestrator = new MemorySearchOrchestrator(
                recentMemoryStore,
                semanticMemoryStore,
                compressedMemoryStore,
                maintenanceService);
        this.memoryMaintenanceService = maintenanceService;
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
            return null;
        }
        return memorySaveOrchestrator.save(record, tenantContext);
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
                                     List<RetrievalPriority> retrievalPriority) {
        if (!hasValidTenantContext(tenantContext)) {
            return new MemorySearchResult(List.of());
        }
        return memorySearchOrchestrator.search(query, tenantContext, retrievalPriority);
    }

    /**
     * 压缩记忆。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩后的记忆记录
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext)) {
            return null;
        }
        return memoryMaintenanceService.compress(request, tenantContext);
    }

    /**
     * 校验租户上下文是否有效。
     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null
                && tenantContext.getTenantId() != null
                && !tenantContext.getTenantId().isBlank();
    }

}
