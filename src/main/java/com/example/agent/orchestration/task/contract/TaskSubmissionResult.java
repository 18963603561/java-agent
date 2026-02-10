package com.example.agent.orchestration.task.contract;

import java.util.Map;

/**
 * 任务提交结果。
 * <p>用途：表达编排层提交任务后的统一返回结构。
 * <p>输入：任务标识、工作流标识、状态、流地址与可选结果。
 * <p>输出：供上层适配器转换为外部响应对象。
 * <p>边界：异步场景下 result 可为空；同步完成场景可返回完整结果。
 */
public class TaskSubmissionResult {

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
     * 事件流地址。
     */
    private String streamUrl;

    /**
     * 任务结果。
     */
    private Map<String, Object> result;

    public TaskSubmissionResult() {
    }

    public TaskSubmissionResult(String taskId, String workflowId, String status) {
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

