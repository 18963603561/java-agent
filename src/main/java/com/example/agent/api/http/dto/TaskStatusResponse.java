package com.example.agent.api.http.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 任务状态响应结构。
 * <p>用途：向外部返回任务状态与执行结果摘要。
 * <p>输入：任务标识、工作流标识、状态与结果。
 * <p>输出：序列化后的响应对象。
 * <p>边界：结果映射允许为空，表示尚无输出。
 * <p>示例：
 * <pre>{@code
 * TaskStatusResponse response = new TaskStatusResponse(taskId, workflowId, "COMPLETED", time, result);
 * }</pre>
 */
public class TaskStatusResponse {

    /**
     * 任务标识。
     * <p>示例：{@code "task-123"}。
     */
    private String taskId;
    /**
     * 工作流标识。
     * <p>示例：{@code "workflow-456"}。
     */
    private String workflowId;
    /**
     * 任务状态。
     * <p>示例：{@code "RUNNING"} 或 {@code "COMPLETED"}。
     */
    private String status;
    /**
     * 最近更新时间。
     * <p>示例：{@code Instant.now()}。
     */
    private Instant updatedAt;
    /**
     * 任务执行结果。
     * <p>示例：{@code {"answer":"ok"}}。
     */
    private Map<String, Object> result;

    /**
     * 空构造方法，便于序列化框架使用。
     * <p>示例：{@code new TaskStatusResponse()}。
     */
    public TaskStatusResponse() {
    }

    /**
     * 全量构造方法。
     *
     * <p>输入：任务标识、工作流标识、状态、更新时间与结果。
     * <p>输出：构造完成的响应对象。
     * <p>示例：
     * <pre>{@code
     * new TaskStatusResponse("task-1", "wf-1", "COMPLETED", Instant.now(), Map.of("answer","ok"));
     * }</pre>
     *
     * @param taskId 任务标识
     * @param workflowId 工作流标识
     * @param status 任务状态
     * @param updatedAt 更新时间
     * @param result 执行结果
     */
    public TaskStatusResponse(String taskId, String workflowId, String status,
                              Instant updatedAt, Map<String, Object> result) {
        this.taskId = taskId;
        this.workflowId = workflowId;
        this.status = status;
        this.updatedAt = updatedAt;
        this.result = result;
    }

    /**
     * 获取任务标识。
     *
     * <p>输出：任务标识字符串。
     * <p>示例：{@code String id = getTaskId();}。
     *
     * @return 任务标识
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 设置任务标识。
     *
     * <p>输入：任务标识字符串。
     * <p>示例：{@code setTaskId("task-123");}。
     *
     * @param taskId 任务标识
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * 获取工作流标识。
     *
     * <p>输出：工作流标识字符串。
     * <p>示例：{@code String id = getWorkflowId();}。
     *
     * @return 工作流标识
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * 设置工作流标识。
     *
     * <p>输入：工作流标识字符串。
     * <p>示例：{@code setWorkflowId("workflow-456");}。
     *
     * @param workflowId 工作流标识
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * 获取任务状态。
     *
     * <p>输出：任务状态值。
     * <p>示例：{@code String status = getStatus();}。
     *
     * @return 任务状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置任务状态。
     *
     * <p>输入：任务状态字符串。
     * <p>示例：{@code setStatus("RUNNING");}。
     *
     * @param status 任务状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取更新时间。
     *
     * <p>输出：更新时间对象。
     * <p>示例：{@code Instant time = getUpdatedAt();}。
     *
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * <p>输入：更新时间对象。
     * <p>示例：{@code setUpdatedAt(Instant.now());}。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取任务结果。
     *
     * <p>输出：结果映射，可能为空。
     * <p>示例：{@code Map<String, Object> result = getResult();}。
     *
     * @return 执行结果
     */
    public Map<String, Object> getResult() {
        return result;
    }

    /**
     * 设置任务结果。
     *
     * <p>输入：结果映射对象。
     * <p>示例：{@code setResult(Map.of("answer","ok"));}。
     *
     * @param result 执行结果
     */
    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
}
