package com.example.agent.orchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 内存任务仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public TaskRecord save(TaskRecord record) {
        if (record == null) {
            return null;
        }
        tasks.put(record.getTaskId(), record);
        if (StringUtils.hasText(record.getIdempotencyKey())) {
            idempotencyIndex.put(buildIdempotencyKey(record.getTenantId(), record.getIdempotencyKey()),
                    record.getTaskId());
        }
        return record;
    }

    @Override
    public TaskRecord findById(String tenantId, String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null || !tenantId.equals(record.getTenantId())) {
            return null;
        }
        return record;
    }

    @Override
    public TaskRecord findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String taskId = idempotencyIndex.get(buildIdempotencyKey(tenantId, idempotencyKey));
        if (taskId == null) {
            return null;
        }
        return findById(tenantId, taskId);
    }

    @Override
    public List<TaskRecord> listByTenant(String tenantId, String status) {
        List<TaskRecord> records = new ArrayList<>();
        for (TaskRecord record : tasks.values()) {
            if (!tenantId.equals(record.getTenantId())) {
                continue;
            }
            if (status != null && record.getStatus() != null && !status.equalsIgnoreCase(record.getStatus())) {
                continue;
            }
            records.add(record);
        }
        records.sort(Comparator.comparing(TaskRecord::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return records;
    }

    private String buildIdempotencyKey(String tenantId, String key) {
        return tenantId + ":" + key;
    }
}
