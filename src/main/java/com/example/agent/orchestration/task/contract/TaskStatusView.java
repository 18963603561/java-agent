package com.example.agent.orchestration.task.contract;

import com.example.agent.orchestration.task.TaskStatus;
import java.time.Instant;
import java.util.Map;

/**
 * 任务状态视图。
 * <p>用途：表达任务状态查询结果的编排层视图对象。
 * <p>输入：任务标识、工作流标识、状态、更新时间、结果。
 * <p>输出：供上层适配器封装为外部接口响应。
 * <p>边界：结果可为空，表示任务尚未产出最终输出。
 */
public class TaskStatusView {

    /**
     * 任务标识。
     */
    private String taskId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 任务状态。
     */
    private TaskStatus status;

    /**
     * 更新时间。
     */
    private Instant updatedAt;

    /**
     * 任务结果。
     */
    private Map<String, Object> result;

    public TaskStatusView() {
    }

    public TaskStatusView(String taskId, String workflowId, TaskStatus status,
                          Instant updatedAt, Map<String, Object> result) {
        this.taskId = taskId;
        this.workflowId = workflowId;
        this.status = status;
        this.updatedAt = updatedAt;
        this.result = result;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
}
