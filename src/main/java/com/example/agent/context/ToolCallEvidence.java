package com.example.agent.context;

/**
 * 工具调用证据，用于记录工具调用的关键信息与摘要。
 */
public class ToolCallEvidence {

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 参数摘要，用于避免记录全量参数。
     */
    private String argsDigest;

    /**
     * 结果摘要，用于避免记录全量结果。
     */
    private String resultDigest;

    /**
     * 调用耗时，单位毫秒。
     */
    private Long durationMs;

    /**
     * 调用状态，表示成功或失败。
     */
    private String status;

    /**
     * 错误码，可为空。
     */
    private String errorCode;

    /**
     * 工具调用标识，可为空，用于关联事件。
     */
    private String toolCallId;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgsDigest() {
        return argsDigest;
    }

    public void setArgsDigest(String argsDigest) {
        this.argsDigest = argsDigest;
    }

    public String getResultDigest() {
        return resultDigest;
    }

    public void setResultDigest(String resultDigest) {
        this.resultDigest = resultDigest;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
