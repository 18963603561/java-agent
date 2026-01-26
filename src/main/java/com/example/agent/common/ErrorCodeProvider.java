package com.example.agent.common;

/**
 * 错误码提供接口，用于统一错误响应。
 */
public interface ErrorCodeProvider {

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    String getErrorCode();
}
