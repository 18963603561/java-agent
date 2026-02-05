package com.example.agent.capabilities.memory;

import java.util.List;

/**
 * 向量存储接口，用于接入外部向量数据库。
 */
public interface VectorStore {

    /**
     * 写入向量数据。
     *
     * @param tenantId 租户标识
     * @param record 记忆记录
     * @param embedding 向量数据
     */
    void upsert(String tenantId, MemoryRecord record, List<Float> embedding);

    /**
     * 向量检索。
     *
     * @param tenantId 租户标识
     * @param sessionId 会话标识
     * @param embedding 查询向量
     * @param limit 结果数量
     * @return 记忆记录列表
     */
    List<MemoryRecord> search(String tenantId, String sessionId, List<Float> embedding, int limit);
}