package com.example.agent.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 任务列表查询参数。
 */
public class TaskQuery {

    /**
     * 任务状态过滤。
     */
    private String status;

    /**
     * 游标。
     */
    private String cursor;

    /**
     * 分页大小。
     */
    @NotNull(message = "分页大小不能为空")
    @Min(value = 1, message = "分页大小不能小于 1")
    @Max(value = 100, message = "分页大小不能超过 100")
    private Integer size;

    public TaskQuery() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
