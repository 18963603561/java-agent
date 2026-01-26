package com.example.agent.memory;

/**
 * 记忆压缩请求。
 */
public class CompressionRequest {

    private String sessionId;
    private String strategy;

    public CompressionRequest() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
