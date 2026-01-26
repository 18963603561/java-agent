package com.example.agent.budget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 预算记录仓储，使用内存存储并提供去重能力。
 */
@Repository
public class TokenUsageRepository {

    private final ConcurrentHashMap<String, TokenUsageRecord> usageIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TokenUsageRecord>> taskIndex = new ConcurrentHashMap<>();

    public boolean saveIfAbsent(TokenUsageRecord record) {
        if (record.getUsageId() == null) {
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

    public List<TokenUsageRecord> findByTask(String tenantId, String taskId) {
        String key = tenantId + ":" + taskId;
        return taskIndex.getOrDefault(key, List.of());
    }
}
