package com.example.agent.capabilities.llm.provider;

/**
 * LLM 领域标准错误码。
 *
 * <p>用途：统一模型供应商异常编码，避免上层依赖字符串分支。
 */
public enum LlmErrorCode {

    /**
     * 请求参数错误。
     */
    MODEL_BAD_REQUEST("MODEL_BAD_REQUEST", false),

    /**
     * 鉴权失败。
     */
    MODEL_AUTH_FAILED("MODEL_AUTH_FAILED", false),

    /**
     * 权限拒绝。
     */
    MODEL_FORBIDDEN("MODEL_FORBIDDEN", false),

    /**
     * 触发限流。
     */
    MODEL_RATE_LIMITED("MODEL_RATE_LIMITED", true),

    /**
     * 远端不可用。
     */
    MODEL_UNAVAILABLE("MODEL_UNAVAILABLE", true),

    /**
     * 网络调用异常。
     */
    MODEL_NETWORK_ERROR("MODEL_NETWORK_ERROR", true),

    /**
     * 响应解析失败。
     */
    MODEL_RESPONSE_PARSE_FAILED("MODEL_RESPONSE_PARSE_FAILED", true),

    /**
     * 配置错误。
     */
    MODEL_CONFIG_INVALID("MODEL_CONFIG_INVALID", false),

    /**
     * 调用超时。
     */
    MODEL_TIMEOUT("MODEL_TIMEOUT", true),

    /**
     * 未分类异常。
     */
    MODEL_UNKNOWN_ERROR("MODEL_UNKNOWN_ERROR", false);

    private final String code;
    private final boolean retriable;

    LlmErrorCode(String code, boolean retriable) {
        this.code = code;
        this.retriable = retriable;
    }

    /**
     * 获取错误码字符串。
     *
     * @return 错误码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 是否可重试。
     *
     * @return 是否可重试
     */
    public boolean isRetriable() {
        return retriable;
    }

    /**
     * 由字符串反查枚举。
     *
     * @param value 字符串错误码
     * @return 标准错误码，未命中返回未知错误码
     */
    public static LlmErrorCode fromCode(String value) {
        if (value == null || value.isBlank()) {
            return MODEL_UNKNOWN_ERROR;
        }
        for (LlmErrorCode errorCode : values()) {
            if (errorCode.code.equalsIgnoreCase(value.trim())) {
                return errorCode;
            }
        }
        return MODEL_UNKNOWN_ERROR;
    }
}

