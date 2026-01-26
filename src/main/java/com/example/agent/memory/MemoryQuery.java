package com.example.agent.memory;

/**
 * 记忆查询请求。
 */
public class MemoryQuery {

    private String sessionId;
    private String query;
    private Integer limit;

    public MemoryQuery() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
