package com.example.agent.planning;

import java.util.Map;

/**
 * 规划请求，描述输入与上下文。
 */
public class PlanRequest {

    private String query;
    private Map<String, Object> context;

    public PlanRequest() {
    }

    public PlanRequest(String query, Map<String, Object> context) {
        this.query = query;
        this.context = context;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}
