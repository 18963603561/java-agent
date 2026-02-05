package com.example.agent.common.error;

import java.util.Map;

/**
 * 统一错误结构，包含错误码与上下文信息。
 */
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, Object> details;
    private String traceId;
    private String requestId;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, Map<String, Object> details, String traceId, String requestId) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.traceId = traceId;
        this.requestId = requestId;
    }

    /**
     * 构建错误响应。
     *
     * @param code 错误码
     * @param message 错误信息
     * @param details 细节信息
     * @param traceId 链路标识
     * @param requestId 请求标识
     * @return 错误响应
     */
    public static ErrorResponse of(String code, String message, Map<String, Object> details,
                                   String traceId, String requestId) {
        return new ErrorResponse(code, message, details, traceId, requestId);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
