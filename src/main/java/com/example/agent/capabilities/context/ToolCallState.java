package com.example.agent.capabilities.context;

import java.time.Instant;

/**
 * 工具调用状态记录。
 */
public class ToolCallState {

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 请求标识。
     */
    private String requestId;

    /**
     * 开始时间。
     */
    private Instant startTime;

    /**
     * 结束时间。
     */
    private Instant endTime;

    /**
     * 是否成功。
     */
    private Boolean success;

    /**
     * 错误码。
     */
    private String errorCode;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}