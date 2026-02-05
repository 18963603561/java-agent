package com.example.agent.budget.token;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存预算记录仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenUsageRepository implements TokenUsageRepository {

    private final ConcurrentHashMap<String, TokenUsageRecord> usageIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TokenUsageRecord>> taskIndex = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(TokenUsageRecord record) {
        if (record == null || record.getUsageId() == null) {
            return false;
        }
        String key = record.getTenantId() + ":" + record.getUsageId();
        TokenUsageRecord existing = usageIndex.putIfAbsent(key, record);
        if (existing != null) {
            return false;
        }
        String taskKey = record.getTenantId() + ":" + record.getTaskId();
        taskIndex.computeIfAbsent(taskKey, k -> new ArrayList<>()).add(record);
        return true;
    }

    @Override
    public List<TokenUsageRecord> findByTask(String tenantId, String taskId) {
        String key = tenantId + ":" + taskId;
        return taskIndex.getOrDefault(key, List.of());
    }
}
