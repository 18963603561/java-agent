package com.example.agent.memory;

import com.example.agent.auth.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 压缩记忆存取层，负责生成与检索 compressed 层数据。
 */
@Service
public class CompressedMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(CompressedMemoryStore.class);

    private final MemoryRepository memoryRepository;

    public CompressedMemoryStore(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * 根据会话记录生成压缩记忆。
     *
     * @param sessionId 会话标识
     * @param records 会话记录
     * @param tenantContext 租户上下文
     * @return 压缩后的记忆记录
     */
    public MemoryRecord compress(String sessionId, List<MemoryRecord> records, TenantContext tenantContext) {
        String summary = buildSummary(records);
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        MemoryRecord compressed = new MemoryRecord();
        compressed.setMemoryId(UUID.randomUUID().toString());
        compressed.setSessionId(sessionId);
        compressed.setSummary(summary);
        compressed.setLayer("compressed");
        compressed.setTenantId(tenantContext.getTenantId());
        compressed.setCreatedAt(Instant.now());
        MemoryRecord saved = memoryRepository.save(compressed);
        log.info("记忆压缩生成, tenantId={}, sessionId={}, memoryId={}",
                tenantContext.getTenantId(), sessionId, compressed.getMemoryId());
        return saved;
    }

    /**
     * 检索 compressed 记录。
     *
     * @param tenantId 租户标识
     * @param sessionId 会话标识
     * @param query 查询条件
     * @param limit 返回数量
     * @return 压缩记录
     */
    public List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit) {
        List<MemoryRecord> records = memoryRepository.search(tenantId, sessionId, query, limit);
        return filterCompressed(records);
    }

    /**
     * 按会话查询 compressed 记录。
     *
     * @param tenantId 租户标识
     * @param sessionId 会话标识
     * @return 压缩记录列表
     */
    public List<MemoryRecord> listBySession(String tenantId, String sessionId) {
        List<MemoryRecord> records = memoryRepository.findBySession(tenantId, sessionId);
        return filterCompressed(records);
    }

    private String buildSummary(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if ("compressed".equalsIgnoreCase(record.getLayer())) {
                continue;
            }
            if (StringUtils.hasText(record.getContent())) {
                summary.append(record.getContent()).append(' ');
            } else if (StringUtils.hasText(record.getSummary())) {
                summary.append(record.getSummary()).append(' ');
            }
        }
        return summary.toString().trim();
    }

    private List<MemoryRecord> filterCompressed(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            if ("compressed".equalsIgnoreCase(record.getLayer())) {
                filtered.add(record);
            }
        }
        return filtered;
    }
}
