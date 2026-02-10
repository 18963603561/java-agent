package com.example.agent.orchestration.task.contract;

import com.example.agent.orchestration.task.TaskStatus;

/**
 * 任务列表查询命令。
 * <p>用途：承载编排层任务列表筛选与分页输入，隔离 HTTP 查询参数对象。
 * <p>输入：状态过滤、游标、分页大小。
 * <p>输出：作为任务查询服务输入参数。
 * <p>边界：分页参数为空时由服务层按默认策略处理。
 */
public class TaskQueryCommand {

    /**
     * 状态过滤条件。
     */
    private TaskStatus status;

    /**
     * 游标。
     */
    private String cursor;

    /**
     * 分页大小。
     */
    private Integer size;

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
