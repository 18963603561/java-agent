package com.example.agent.common;

import java.util.Map;

/**
 * 任务提交响应结构。
 */
public class TaskResponse {

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
    private String status;
    /**
     * 事件流订阅地址，便于继续追踪任务。
     */
    private String streamUrl;
    /**
     * 任务结果，仅在同步完成时返回。
     */
    private Map<String, Object> result;

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

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
}
