package com.example.agent.history.timeline;

/**
 * 时间线查询请求。
 */
public class TimelineRequest {

    private String workflowId;
    private String mode;

    public TimelineRequest() {
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
