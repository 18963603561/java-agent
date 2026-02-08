package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.CompressedMemoryStore;
import com.example.agent.capabilities.memory.CompressionRequest;
import com.example.agent.capabilities.memory.MemoryExpireProperties;
import com.example.agent.capabilities.memory.MemoryExpirationService;
import com.example.agent.capabilities.memory.MemoryPolicy;
import com.example.agent.capabilities.memory.MemoryRecord;
import com.example.agent.capabilities.memory.MemoryRepository;
import com.example.agent.security.auth.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 记忆维护服务，统一负责自动压缩与过期清理触发。
 */
@Service
public class MemoryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenanceService.class);

    /**
     * 记忆仓储。
     */
    private final MemoryRepository memoryRepository;

    /**
     * 压缩存储服务。
     */
    private final CompressedMemoryStore compressedMemoryStore;

    /**
     * 压缩策略。
     */
    private final MemoryPolicy memoryPolicy;

    /**
     * 过期策略配置。
     */
    private final MemoryExpireProperties expireProperties;

    /**
     * 过期处理服务。
     */
    private final MemoryExpirationService expirationService;

    /**
     * 每租户最近清理时间戳，避免高频清理造成压力。
     */
    private final Map<String, Instant> cleanupTimestamps = new ConcurrentHashMap<>();

    public MemoryMaintenanceService(MemoryRepository memoryRepository,
                                    CompressedMemoryStore compressedMemoryStore,
                                    MemoryPolicy memoryPolicy,
                                    MemoryExpireProperties expireProperties,
                                    MemoryExpirationService expirationService) {
        this.memoryRepository = memoryRepository;
        this.compressedMemoryStore = compressedMemoryStore;
        this.memoryPolicy = memoryPolicy;
        this.expireProperties = expireProperties;
        this.expirationService = expirationService;
    }

    /**
     * 执行手动压缩请求。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩结果
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext)) {
            log.warn("记忆压缩跳过, reason=tenant_invalid");
            return null;
        }
        if (request == null) {
            log.warn("记忆压缩跳过, tenantId={}, reason=request_missing", tenantContext.getTenantId());
            return null;
        }
        if (!StringUtils.hasText(request.getSessionId())) {
            log.warn("记忆压缩跳过, tenantId={}, reason=session_missing", tenantContext.getTenantId());
            return null;
        }
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

    /**
     * 按策略触发自动压缩。
     *
     * @param sessionId 会话标识
     * @param tenantContext 租户上下文
     */
    public void autoCompressIfNeeded(String sessionId, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext) || !StringUtils.hasText(sessionId)) {
            return;
        }
        List<MemoryRecord> records = memoryRepository.findBySession(tenantContext.getTenantId(), sessionId);
        if (memoryPolicy != null && memoryPolicy.shouldCompress(records, Instant.now())) {
            MemoryRecord compressed = compressedMemoryStore.compress(sessionId, records, tenantContext);
            if (compressed != null) {
                log.info("自动压缩触发, tenantId={}, sessionId={}, memoryId={}",
                        tenantContext.getTenantId(), sessionId, compressed.getMemoryId());
            }
        }
    }

    /**
     * 按策略触发过期清理。
     *
     * @param tenantContext 租户上下文
     * @param reason 触发原因
     */
    public void cleanupExpiredIfNeeded(TenantContext tenantContext, String reason) {
        if (!hasValidTenantContext(tenantContext) || expireProperties == null || memoryRepository == null) {
            return;
        }
        if (!expireProperties.isEnabled() || !expireProperties.isCleanupOnRead()) {
            return;
        }
        Instant now = Instant.now();
        String tenantId = tenantContext.getTenantId();
        long interval = Math.max(0, expireProperties.getCleanupIntervalSeconds());
        if (interval > 0) {
            Instant lastCleanup = cleanupTimestamps.get(tenantId);
            if (lastCleanup != null && Duration.between(lastCleanup, now).getSeconds() < interval) {
                return;
            }
        }
        int removed = memoryRepository.deleteExpired(tenantId, now);
        cleanupTimestamps.put(tenantId, now);
        if (removed > 0) {
            log.info("过期记忆清理完成, tenantId={}, removed={}, reason={}", tenantId, removed, reason);
        } else {
            log.debug("过期记忆清理无数据, tenantId={}, reason={}", tenantId, reason);
        }
    }

    /**
     * 过滤过期记录。
     *
     * @param records 原始记录
     * @return 过滤后的记录
     */
    public List<MemoryRecord> filterExpired(List<MemoryRecord> records) {
        if (expirationService == null) {
            return records;
        }
        return expirationService.filterExpired(records, Instant.now());
    }

    /**
     * 校验租户上下文是否有效。
     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null && StringUtils.hasText(tenantContext.getTenantId());
    }
}
