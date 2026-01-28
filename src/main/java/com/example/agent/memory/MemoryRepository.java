package com.example.agent.memory;

import java.time.Instant;
import java.util.List;

/**
 * 记忆仓储接口，用于持久化记忆与查询。
 */
public interface MemoryRepository {

    MemoryRecord save(MemoryRecord record);

    List<MemoryRecord> findBySession(String tenantId, String sessionId);

    List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit);

    /**
     * 删除过期记忆。
     *
     * @param tenantId 租户标识
     * @param now 当前时间
     * @return 删除数量
     */
    int deleteExpired(String tenantId, Instant now);
}
