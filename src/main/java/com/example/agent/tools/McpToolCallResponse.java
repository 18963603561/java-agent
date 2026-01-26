package com.example.agent.tools;

import java.util.Map;

/**
 * MCP 工具调用响应。
 */
public class McpToolCallResponse {

    private String callId;
    private String status;
    private Map<String, Object> result;
    private Map<String, Object> error;

    public McpToolCallResponse() {
    }

    public McpToolCallResponse(String callId, String status, Map<String, Object> result, Map<String, Object> error) {
        this.callId = callId;
        this.status = status;
        this.result = result;
        this.error = error;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }
}
