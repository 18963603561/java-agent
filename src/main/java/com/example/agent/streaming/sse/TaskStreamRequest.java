package com.example.agent.streaming.sse;

import java.util.List;

/**
 * 事件流订阅请求参数。
 */
public class TaskStreamRequest {

    private String workflowId;
    private List<String> types;
    private String lastEventId;
    private String cursor;

    public TaskStreamRequest() {
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}
