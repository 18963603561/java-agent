package com.example.agent.scheduler;

/**
 * 调度查询参数。
 */
public class ScheduleQuery {

    private String status;
    private String cursor;
    private Integer size;

    public ScheduleQuery() {
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
