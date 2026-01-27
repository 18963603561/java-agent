package com.example.agent.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 内存调度执行记录仓储，用于无持久化依赖时的兜底实现。
 */
@Repository
@ConditionalOnProperty(prefix = "agent.storage", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryScheduleExecutionRepository implements ScheduleExecutionRepository {

    private final ConcurrentHashMap<String, List<ScheduleExecutionRecord>> executions = new ConcurrentHashMap<>();

    @Override
    public void save(ScheduleExecutionRecord record) {
        String key = record.getTenantId() + ":" + record.getScheduleId();
        executions.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
    }

    @Override
    public List<ScheduleExecutionRecord> findBySchedule(String tenantId, String scheduleId) {
        String key = tenantId + ":" + scheduleId;
        return executions.getOrDefault(key, List.of());
    }
}
