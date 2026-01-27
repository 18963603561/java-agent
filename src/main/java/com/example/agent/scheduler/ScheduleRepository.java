package com.example.agent.scheduler;

import java.util.List;

/**
 * 调度任务仓储接口，用于持久化调度信息。
 */
public interface ScheduleRepository {

    ScheduleSpec save(ScheduleSpec spec);

    ScheduleSpec findById(String tenantId, String scheduleId);

    String findByIdempotencyKey(String tenantId, String idempotencyKey);

    void delete(String tenantId, String scheduleId);

    List<ScheduleSpec> list(String tenantId);
}
