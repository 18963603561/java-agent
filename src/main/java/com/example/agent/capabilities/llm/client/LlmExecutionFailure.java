package com.example.agent.capabilities.llm.client;

/**
 * LLM 调用失败信息。
 *
 * <p>用途：统一承载错误码、错误消息与可重试信息。
 * <p>输入：模型调用异常、错误映射结果。
 * <p>输出：统一结果协议中的 failure 字段。
 */
public class LlmExecutionFailure {

    /**
     * 标准错误码。
     */
    private final String errorCode;

    /**
     * 错误消息。
     */
    private final String errorMessage;

    /**
     * 原始异常类型。
     */
    private final String exceptionType;

    /**
     * 是否可重试。
     */
    private final boolean retriable;

    /**
     * 构造失败信息对象。
     *
     * @param errorCode 标准错误码
     * @param errorMessage 错误消息
     * @param exceptionType 异常类型
     * @param retriable 是否可重试
     */
    public LlmExecutionFailure(String errorCode, String errorMessage, String exceptionType, boolean retriable) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.exceptionType = exceptionType;
        this.retriable = retriable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public boolean isRetriable() {
        return retriable;
    }
}

