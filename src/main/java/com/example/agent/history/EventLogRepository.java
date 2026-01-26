package com.example.agent.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

/**
 * 事件日志仓储，使用内存实现事件持久化与去重。
 */
@Repository
public class EventLogRepository {

    private final ConcurrentHashMap<String, EventLogRecord> eventIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EventLogRecord>> workflowIndex = new ConcurrentHashMap<>();

    /**
     * 幂等保存事件日志。
     *
     * @param record 事件记录
     * @return 是否写入成功
     */
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

    /**
     * 查询工作流事件列表。
     *
     * @param tenantId 租户标识
     * @param workflowId 工作流标识
     * @return 事件列表
     */
    public List<EventLogRecord> findByWorkflow(String tenantId, String workflowId) {
        String key = tenantId + ":" + workflowId;
        List<EventLogRecord> records = workflowIndex.get(key);
        if (records == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(records);
    }
}
