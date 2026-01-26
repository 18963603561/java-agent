package com.example.agent.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 调度执行记录仓储，使用内存存储。
 */
@Repository
public class ScheduleExecutionRepository {

    private final ConcurrentHashMap<String, List<ScheduleExecutionRecord>> executions = new ConcurrentHashMap<>();

    public void save(ScheduleExecutionRecord record) {
        String key = record.getTenantId() + ":" + record.getScheduleId();
        executions.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
    }

    public List<ScheduleExecutionRecord> findBySchedule(String tenantId, String scheduleId) {
        String key = tenantId + ":" + scheduleId;
        return executions.getOrDefault(key, List.of());
    }
}
