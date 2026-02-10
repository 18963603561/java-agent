package com.example.agent.orchestration.task;

/**
 * 任务仓储接口，用于持久化任务状态与查询。
 */
public interface TaskRepository {

    TaskRecord save(TaskRecord record);

    TaskRecord findById(String tenantId, String taskId);

    TaskRecord findByIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * 按租户与筛选条件查询任务分页结果。
     * <p>排序规则固定为 {@code updatedAt DESC, taskId DESC}，用于保障游标翻页稳定性。
     */
    TaskPageResult listPage(TaskPageQuery query);
}
