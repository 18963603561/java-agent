package com.example.agent.orchestrator;

import java.util.List;

/**
 * 任务仓储接口，用于持久化任务状态与查询。
 */
public interface TaskRepository {

    TaskRecord save(TaskRecord record);

    TaskRecord findById(String tenantId, String taskId);

    TaskRecord findByIdempotencyKey(String tenantId, String idempotencyKey);

    List<TaskRecord> listByTenant(String tenantId, String status);
}
