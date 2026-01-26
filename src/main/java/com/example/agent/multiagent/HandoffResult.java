package com.example.agent.multiagent;

import java.util.Map;

/**
 * 智能体交接结果。
 */
public class HandoffResult {

    private String status;
    private Map<String, Object> context;

    public HandoffResult() {
    }

    public HandoffResult(String status, Map<String, Object> context) {
        this.status = status;
        this.context = context;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}
