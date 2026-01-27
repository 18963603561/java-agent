package com.example.agent.memory;

import java.util.List;

/**
 * 记忆仓储接口，用于持久化记忆与查询。
 */
public interface MemoryRepository {

    MemoryRecord save(MemoryRecord record);

    List<MemoryRecord> findBySession(String tenantId, String sessionId);

    List<MemoryRecord> search(String tenantId, String sessionId, String query, int limit);
}
