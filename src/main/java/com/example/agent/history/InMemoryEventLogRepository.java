package com.example.agent.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存事件日志仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryEventLogRepository implements EventLogRepository {

    private final ConcurrentHashMap<String, EventLogRecord> eventIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EventLogRecord>> workflowIndex = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(EventLogRecord record) {
        if (record == null || record.getEventId() == null) {
            return false;
        }
        EventLogRecord existing = eventIndex.putIfAbsent(record.getEventId(), record);
        if (existing != null) {
            return false;
        }
        String key = record.getTenantId() + ":" + record.getWorkflowId();
        workflowIndex.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(record);
        return true;
    }

    @Override
    public List<EventLogRecord> findByWorkflow(String tenantId, String workflowId) {
        String key = tenantId + ":" + workflowId;
        List<EventLogRecord> records = workflowIndex.get(key);
        if (records == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(records);
    }
}
