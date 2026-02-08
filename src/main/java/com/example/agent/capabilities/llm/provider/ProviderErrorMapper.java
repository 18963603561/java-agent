package com.example.agent.capabilities.llm.provider;

import com.example.agent.common.error.ErrorCodeException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * 提供商错误映射器。
 *
 * <p>用途：将 HTTP 状态统一映射为业务错误码。</p>
 */
@Component
public class ProviderErrorMapper {

    /**
     * 映射模型接口错误。
     *
     * @param status HTTP 状态
     * @param message 错误消息
     * @return 业务异常
     */
    public ErrorCodeException mapModelError(HttpStatus status, String message) {
        LlmErrorCode errorCode = mapHttpStatus(status);
        HttpStatus responseStatus = resolveResponseStatus(errorCode, status);
        return new ErrorCodeException(responseStatus, errorCode.getCode(), message);
    }

    /**
     * 映射异常对象。
     *
     * @param throwable 异常对象
     * @return 业务异常
     */
    public ErrorCodeException mapThrowable(Throwable throwable) {
        if (throwable instanceof ErrorCodeException errorCodeException) {
            return errorCodeException;
        }
        if (throwable instanceof WebClientRequestException webClientRequestException) {
            LlmErrorCode errorCode = LlmErrorCode.MODEL_NETWORK_ERROR;
            String message = webClientRequestException.getMessage();
            return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, errorCode.getCode(), message);
        }
        if (throwable instanceof TimeoutException) {
            LlmErrorCode errorCode = LlmErrorCode.MODEL_TIMEOUT;
            String message = throwable.getMessage() == null ? "模型调用超时" : throwable.getMessage();
            return new ErrorCodeException(HttpStatus.GATEWAY_TIMEOUT, errorCode.getCode(), message);
        }
        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException) {
            LlmErrorCode errorCode = LlmErrorCode.MODEL_CONFIG_INVALID;
            String message = throwable.getMessage() == null ? "模型配置非法" : throwable.getMessage();
            return new ErrorCodeException(HttpStatus.BAD_REQUEST, errorCode.getCode(), message);
        }
        if (throwable != null && throwable.getCause() instanceof TimeoutException) {
            LlmErrorCode errorCode = LlmErrorCode.MODEL_TIMEOUT;
            String message = throwable.getMessage() == null ? "模型调用超时" : throwable.getMessage();
            return new ErrorCodeException(HttpStatus.GATEWAY_TIMEOUT, errorCode.getCode(), message);
        }
        if (throwable != null) {
            String message = throwable.getMessage() == null ? "模型调用失败" : throwable.getMessage();
            return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                    LlmErrorCode.MODEL_UNKNOWN_ERROR.getCode(),
                    message);
        }
        return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE,
                LlmErrorCode.MODEL_UNKNOWN_ERROR.getCode(),
                "模型调用失败");
    }

    /**
     * 解析错误码的可重试属性。
     *
     * @param errorCode 错误码字符串
     * @return 是否可重试
     */
    public boolean isRetriable(String errorCode) {
        return LlmErrorCode.fromCode(errorCode).isRetriable();
    }

    private LlmErrorCode mapHttpStatus(HttpStatus status) {
        if (status == null) {
            return LlmErrorCode.MODEL_UNAVAILABLE;
        }
        if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY) {
            return LlmErrorCode.MODEL_BAD_REQUEST;
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return LlmErrorCode.MODEL_AUTH_FAILED;
        }
        if (status == HttpStatus.FORBIDDEN) {
            return LlmErrorCode.MODEL_FORBIDDEN;
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return LlmErrorCode.MODEL_RATE_LIMITED;
        }
        if (status == HttpStatus.REQUEST_TIMEOUT || status == HttpStatus.GATEWAY_TIMEOUT) {
            return LlmErrorCode.MODEL_TIMEOUT;
        }
        if (status.is5xxServerError()) {
            return LlmErrorCode.MODEL_UNAVAILABLE;
        }
        return LlmErrorCode.MODEL_BAD_REQUEST;
    }

    private HttpStatus resolveResponseStatus(LlmErrorCode errorCode, HttpStatus rawStatus) {
        if (errorCode == LlmErrorCode.MODEL_TIMEOUT) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (errorCode == LlmErrorCode.MODEL_UNAVAILABLE || errorCode == LlmErrorCode.MODEL_NETWORK_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (errorCode == LlmErrorCode.MODEL_UNKNOWN_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (rawStatus != null) {
            return rawStatus;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
