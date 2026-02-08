package com.example.agent.capabilities.memory.store;

import com.example.agent.capabilities.memory.store.CompressedMemoryStore;
import com.example.agent.capabilities.memory.model.CompressionRequest;
import com.example.agent.capabilities.memory.policy.MemoryExpirationService;
import com.example.agent.capabilities.memory.policy.MemoryPolicy;
import com.example.agent.capabilities.memory.model.MemoryRecord;
import com.example.agent.capabilities.memory.config.MemoryExpireProperties;
import com.example.agent.capabilities.memory.repository.MemoryRepository;
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
 * 璁板繂缁存姢鏈嶅姟锛岀粺涓€璐熻矗鑷姩鍘嬬缉涓庤繃鏈熸竻鐞嗚Е鍙戙€? */
@Service
public class MemoryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenanceService.class);

    /**
     * 璁板繂浠撳偍銆?     */
    private final MemoryRepository memoryRepository;

    /**
     * 鍘嬬缉瀛樺偍鏈嶅姟銆?     */
    private final CompressedMemoryStore compressedMemoryStore;

    /**
     * 鍘嬬缉绛栫暐銆?     */
    private final MemoryPolicy memoryPolicy;

    /**
     * 杩囨湡绛栫暐閰嶇疆銆?     */
    private final MemoryExpireProperties expireProperties;

    /**
     * 杩囨湡澶勭悊鏈嶅姟銆?     */
    private final MemoryExpirationService expirationService;

    /**
     * 姣忕鎴锋渶杩戞竻鐞嗘椂闂存埑锛岄伩鍏嶉珮棰戞竻鐞嗛€犳垚鍘嬪姏銆?     */
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
     * 鎵ц鎵嬪姩鍘嬬缉璇锋眰銆?     *
     * @param request 鍘嬬缉璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @return 鍘嬬缉缁撴灉
     */
    public MemoryRecord compress(CompressionRequest request, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext)) {
            log.warn("璁板繂鍘嬬缉璺宠繃, reason=tenant_invalid");
            return null;
        }
        if (request == null) {
            log.warn("璁板繂鍘嬬缉璺宠繃, tenantId={}, reason=request_missing", tenantContext.getTenantId());
            return null;
        }
        if (!StringUtils.hasText(request.getSessionId())) {
            log.warn("璁板繂鍘嬬缉璺宠繃, tenantId={}, reason=session_missing", tenantContext.getTenantId());
            return null;
        }
        cleanupExpiredIfNeeded(tenantContext, "compress");
        List<MemoryRecord> records = memoryRepository.findBySession(
                tenantContext.getTenantId(), request.getSessionId());
        MemoryRecord compressed = compressedMemoryStore.compress(
                request.getSessionId(), records, tenantContext, request.getWorkflowId());
        if (compressed == null) {
            log.warn("璁板繂鍘嬬缉鏃犳晥, tenantId={}, sessionId={}",
                    tenantContext.getTenantId(), request.getSessionId());
            return null;
        }
        log.info("璁板繂鍘嬬缉瀹屾垚, tenantId={}, sessionId={}",
                tenantContext.getTenantId(), request.getSessionId());
        return compressed;
    }

    /**
     * 鎸夌瓥鐣ヨЕ鍙戣嚜鍔ㄥ帇缂┿€?     *
     * @param sessionId 浼氳瘽鏍囪瘑
     * @param tenantContext 绉熸埛涓婁笅鏂?     */
    public void autoCompressIfNeeded(String sessionId, TenantContext tenantContext) {
        if (!hasValidTenantContext(tenantContext) || !StringUtils.hasText(sessionId)) {
            return;
        }
        List<MemoryRecord> records = memoryRepository.findBySession(tenantContext.getTenantId(), sessionId);
        if (memoryPolicy != null && memoryPolicy.shouldCompress(records, Instant.now())) {
            MemoryRecord compressed = compressedMemoryStore.compress(sessionId, records, tenantContext);
            if (compressed != null) {
                log.info("鑷姩鍘嬬缉瑙﹀彂, tenantId={}, sessionId={}, memoryId={}",
                        tenantContext.getTenantId(), sessionId, compressed.getMemoryId());
            }
        }
    }

    /**
     * 鎸夌瓥鐣ヨЕ鍙戣繃鏈熸竻鐞嗐€?     *
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param reason 瑙﹀彂鍘熷洜
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
            log.info("杩囨湡璁板繂娓呯悊瀹屾垚, tenantId={}, removed={}, reason={}", tenantId, removed, reason);
        } else {
            log.debug("杩囨湡璁板繂娓呯悊鏃犳暟鎹? tenantId={}, reason={}", tenantId, reason);
        }
    }

    /**
     * 杩囨护杩囨湡璁板綍銆?     *
     * @param records 鍘熷璁板綍
     * @return 杩囨护鍚庣殑璁板綍
     */
    public List<MemoryRecord> filterExpired(List<MemoryRecord> records) {
        if (expirationService == null) {
            return records;
        }
        return expirationService.filterExpired(records, Instant.now());
    }

    /**
     * 鏍￠獙绉熸埛涓婁笅鏂囨槸鍚︽湁鏁堛€?     */
    private boolean hasValidTenantContext(TenantContext tenantContext) {
        return tenantContext != null && StringUtils.hasText(tenantContext.getTenantId());
    }
}

