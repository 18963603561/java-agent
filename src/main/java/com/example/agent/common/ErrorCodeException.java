package com.example.agent.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 带业务错误码的响应异常。
 */
public class ErrorCodeException extends ResponseStatusException implements ErrorCodeProvider {

    /**
     * 业务错误码。
     */
    private final String errorCode;

    public ErrorCodeException(HttpStatus status, String errorCode, String reason) {
        super(status, reason);
        this.errorCode = errorCode;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
