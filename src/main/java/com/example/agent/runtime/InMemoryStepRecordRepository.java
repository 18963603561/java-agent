package com.example.agent.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存步骤记录仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryStepRecordRepository implements StepRecordRepository {

    private final ConcurrentHashMap<String, List<StepRecord>> stepStore = new ConcurrentHashMap<>();

    @Override
    public void save(StepRecord record) {
        String indexKey = buildIndexKey(record.getTenantId(), record.getWorkflowId());
        stepStore.computeIfAbsent(indexKey, key -> new CopyOnWriteArrayList<>());
        stepStore.get(indexKey).add(record);
    }

    @Override
    public List<StepRecord> findByWorkflow(String tenantId, String workflowId) {
        String indexKey = buildIndexKey(tenantId, workflowId);
        List<StepRecord> records = stepStore.get(indexKey);
        if (records == null) {
            return List.of();
        }
        return new ArrayList<>(records);
    }

    private String buildIndexKey(String tenantId, String workflowId) {
        return tenantId + ":" + workflowId;
    }
}
