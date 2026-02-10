package com.example.agent.orchestration.task;

/**
 * 任务仓储异常，用于表示持久化读写不可恢复错误。
 */
public class TaskRepositoryException extends RuntimeException {

    /**
     * 仓储操作名称。
     */
    private final String operation;

    /**
     * 租户标识。
     */
    private final String tenantId;

    /**
     * 任务标识。
     */
    private final String taskId;

    /**
     * 工作流标识。
     */
    private final String workflowId;

    /**
     * 构造仓储异常。
     *
     * @param operation 仓储操作名称
     * @param tenantId 租户标识
     * @param taskId 任务标识
     * @param workflowId 工作流标识
     * @param cause 原始异常
     */
    public TaskRepositoryException(String operation,
                                   String tenantId,
                                   String taskId,
                                   String workflowId,
                                   Throwable cause) {
        super("task_repository_error:" + operation, cause);
        this.operation = operation;
        this.tenantId = tenantId;
        this.taskId = taskId;
        this.workflowId = workflowId;
    }

    public String getOperation() {
        return operation;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
