package com.example.agent.common;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 任务提交请求结构。
 */
public class TaskRequest {

    /**
     * 任务查询内容。
     */
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /**
     * 会话标识，可选。
     */
    private String sessionId;

    /**
     * 任务上下文，可选。
     */
    private Map<String, Object> context;

    /**
     * 幂等键。
     */
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;

    public TaskRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
