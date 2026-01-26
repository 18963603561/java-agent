package com.example.agent.common;

/**
 * 任务提交响应结构。
 */
public class TaskResponse {

    private String taskId;
    private String workflowId;
    private String status;

    public TaskResponse() {
    }

    public TaskResponse(String taskId, String workflowId, String status) {
        this.taskId = taskId;
        this.workflowId = workflowId;
        this.status = status;
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
}
