package com.example.agent.scheduler;

import java.util.List;

/**
 * 调度执行记录仓储接口，用于持久化调度执行历史。
 */
public interface ScheduleExecutionRepository {

    void save(ScheduleExecutionRecord record);

    List<ScheduleExecutionRecord> findBySchedule(String tenantId, String scheduleId);
}
