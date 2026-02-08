package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.MemoryQuery;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.MemorySearchResult;
import com.example.agent.capabilities.memory.RecentMemoryStore;
import com.example.agent.capabilities.memory.RetrievalPriority;
import com.example.agent.capabilities.memory.SemanticMemoryStore;
import com.example.agent.capabilities.memory.support.RetrievalPriorityUtils;
import com.example.agent.security.auth.TenantContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆检索编排服务，负责按优先级聚合 recent、semantic 与 summary 结果。
 */
@Service
public class MemorySearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchOrchestrator.class);

    /**
     * Recent 层检索服务。
     */
    private final RecentMemoryStore recentMemoryStore;

    /**
     * Semantic 层检索服务。
     */
    private final SemanticMemoryStore semanticMemoryStore;

    /**
     * Summary 层检索服务。
     */
    private final CompressedMemoryStore compressedMemoryStore;

    /**
     * 维护服务。
     */
    private final MemoryMaintenanceService memoryMaintenanceService;

    public MemorySearchOrchestrator(RecentMemoryStore recentMemoryStore,
                                    SemanticMemoryStore semanticMemoryStore,
                                    CompressedMemoryStore compressedMemoryStore,
                                    MemoryMaintenanceService memoryMaintenanceService) {
        this.recentMemoryStore = recentMemoryStore;
        this.semanticMemoryStore = semanticMemoryStore;
        this.compressedMemoryStore = compressedMemoryStore;
        this.memoryMaintenanceService = memoryMaintenanceService;
    }

    /**
     * 检索记忆。
     *
     * @param query 查询参数
     * @param tenantContext 租户上下文
     * @param retrievalPriority 检索优先级
     * @return 检索结果
     */
    public MemorySearchResult search(MemoryQuery query,
                                     TenantContext tenantContext,
                                     List<RetrievalPriority> retrievalPriority) {
        if (!hasValidTenantContext(tenantContext)) {
            log.warn("记忆检索跳过, reason=tenant_invalid");
            return new MemorySearchResult(List.of());
        }
        if (query == null) {
            log.warn("记忆检索跳过, tenantId={}, reason=query_missing", tenantContext.getTenantId());
            return new MemorySearchResult(List.of());
        }

        int limit = query.getLimit() != null && query.getLimit() > 0 ? query.getLimit() : 10;
        memoryMaintenanceService.cleanupExpiredIfNeeded(tenantContext, "search");

        List<MemoryRecord> aggregated = new ArrayList<>();
        if (StringUtils.hasText(query.getQuery())) {
            List<RetrievalPriority> priorityOrder = RetrievalPriorityUtils.normalizeOrder(retrievalPriority);
            for (RetrievalPriority priority : priorityOrder) {
                if (aggregated.size() >= limit) {
                    break;
                }
                mergeByPriority(aggregated, query, tenantContext, limit, priority);
            }
        }

        memoryMaintenanceService.autoCompressIfNeeded(query.getSessionId(), tenantContext);
        List<MemoryRecord> filtered = memoryMaintenanceService.filterExpired(aggregated);
        return new MemorySearchResult(filtered);
    }

    /**
     * 按优先级执行检索并合并结果。
     */
    private void mergeByPriority(List<MemoryRecord> aggregated,
                                 MemoryQuery query,
                                 TenantContext tenantContext,
                                 int limit,
                                 RetrievalPriority priority) {
        switch (priority) {
            case RECENT -> {
                List<MemoryRecord> recent = recentMemoryStore.search(
                        tenantContext.getTenantId(), query.getSessionId(), query.getQuery(), limit);
                mergeRecords(aggregated, recent, limit);
            }
            case SEMANTIC -> {
                List<MemoryRecord> semantic = semanticMemoryStore.search(query, tenantContext, limit);
                mergeRecords(aggregated, semantic, limit);
            }
            case SUMMARY -> {
                List<MemoryRecord> compressed = compressedMemoryStore.search(
                        tenantContext.getTenantId(), query.getSessionId(), query.getQuery(), limit);
                mergeRecords(aggregated, compressed, limit);
            }
            default -> {
                // no-op
            }
        }
    }

    /**
     * 合并记录并按 memoryId 去重。
     */
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

    /**
     * 校验租户上下文是否有效。
     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null && StringUtils.hasText(tenantContext.getTenantId());
    }
}
