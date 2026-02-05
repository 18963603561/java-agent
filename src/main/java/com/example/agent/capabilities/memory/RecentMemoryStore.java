package com.example.agent.capabilities.memory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 最近记忆存取层，负责保存与检索 recent 层数据。
 */
@Service
public class RecentMemoryStore {

    private final MemoryRepository memoryRepository;

    public RecentMemoryStore(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /**
     * 保存 recent 记忆。
     *
     * @param record 记忆记录
     * @return 保存结果
     */
    public MemoryRecord save(MemoryRecord record) {
        if (record == null) {
            return null;
        }
        if (!StringUtils.hasText(record.getLayer())) {
            record.setLayer("recent");
        }
        return memoryRepository.save(record);
    }

    /**
     * 按会话查询 recent 记录。
     *
     * @param tenantId 租户标识
     * @param sessionId 会话标识
     * @return recent 记录列表
     */
    public List<MemoryRecord> listBySession(String tenantId, String sessionId) {
        List<MemoryRecord> records = memoryRepository.findBySession(tenantId, sessionId);
        return filterRecent(records);
    }

    /**
     * 检索 recent 记录。
     *
     * @param tenantId 租户标识
     * @param sessionId 会话标识
     * @param query 查询条件
     * @param limit 返回数量
     * @return recent 记录列表
     */
    public List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit) {
        List<MemoryRecord> records = memoryRepository.search(tenantId, sessionId, query, limit);
        return filterRecent(records);
    }

    private List<MemoryRecord> filterRecent(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> filtered = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record == null) {
                continue;
            }
            String layer = record.getLayer();
            if (!StringUtils.hasText(layer) || "recent".equalsIgnoreCase(layer)) {
                filtered.add(record);
            }
        }
        return filtered;
    }
}
