package com.example.agent.runtime;

/**
 * 步骤时间线查询参数。
 */
public class StepQuery {

    private String workflowId;
    private String cursor;
    private Integer size;

    public StepQuery() {
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
