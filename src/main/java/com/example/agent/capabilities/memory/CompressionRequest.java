package com.example.agent.capabilities.memory;

/**
 * 记忆压缩请求。
 */
public class CompressionRequest {

    private String sessionId;
    /**
     * 工作流标识，用于压缩日志追踪，可为空。
     */
    private String workflowId;

    public CompressionRequest() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
}
