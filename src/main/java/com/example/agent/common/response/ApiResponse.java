package com.example.agent.common.response;

/**
 * 统一响应结构，承载业务数据与调用链上下文信息。
 *
 * @param <T> 响应数据类型
 */
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private String requestId;

    public ApiResponse() {
    }

    public ApiResponse(String code, String message, T data, String traceId, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.requestId = requestId;
    }

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param traceId 链路标识
     * @param requestId 请求标识
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data, String traceId, String requestId) {
        return new ApiResponse<>("OK", "success", data, traceId, requestId);
    }

    /**
     * 构建错误响应。
     *
     * @param code 错误码
     * @param message 错误信息
     * @param traceId 链路标识
     * @param requestId 请求标识
     * @param <T> 数据类型
     * @return 错误响应
     */
    public static <T> ApiResponse<T> error(String code, String message, String traceId, String requestId) {
        return new ApiResponse<>(code, message, null, traceId, requestId);
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
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
