package com.example.agent.budget.token;

import java.util.List;

/**
 * 预算记录仓储接口，用于持久化与查询预算使用情况。
 */
public interface TokenUsageRepository {

    /**
     * 幂等保存预算记录。
     *
     * @param record 预算记录
     * @return 是否写入成功
     */
    boolean saveIfAbsent(TokenUsageRecord record);

    /**
     * 查询任务维度预算记录。
     *
     * @param tenantId 租户标识
     * @param taskId 任务标识
     * @return 预算记录列表
     */
    List<TokenUsageRecord> findByTask(String tenantId, String taskId);
}
