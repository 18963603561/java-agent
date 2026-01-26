package com.example.agent.memory;

import java.util.List;

/**
 * 向量存储接口，允许接入外部向量数据库。
 */
public interface VectorStore {

    /**
     * 向量搜索。
     *
     * @param tenantId 租户标识
     * @param query 查询语句
     * @param limit 结果数量
     * @return 记忆记录列表
     */
    List<MemoryRecord> search(String tenantId, String query, int limit);
}
