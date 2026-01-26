package com.example.agent.history;

/**
 * 事件日志查询参数。
 */
public class EventQuery {

    private String workflowId;
    private String cursor;
    private Integer size;

    public EventQuery() {
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
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
