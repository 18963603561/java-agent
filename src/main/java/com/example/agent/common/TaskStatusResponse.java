package com.example.agent.common;

import java.time.Instant;

/**
 * 任务状态响应结构。
 */
public class TaskStatusResponse {

    private String taskId;
    private String workflowId;
    private String status;
    private Instant updatedAt;

    public TaskStatusResponse() {
    }

    public TaskStatusResponse(String taskId, String workflowId, String status, Instant updatedAt) {
        this.taskId = taskId;
        this.workflowId = workflowId;
        this.status = status;
        this.updatedAt = updatedAt;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
