package com.example.agent.capabilities.llm.provider;

import com.example.agent.common.error.ErrorCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
        if (status == null) {
            return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message);
        }
        if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY) {
            return new ErrorCodeException(status, "MODEL_BAD_REQUEST", message);
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return new ErrorCodeException(status, "MODEL_AUTH_FAILED", message);
        }
        if (status == HttpStatus.FORBIDDEN) {
            return new ErrorCodeException(status, "MODEL_FORBIDDEN", message);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new ErrorCodeException(status, "MODEL_RATE_LIMITED", message);
        }
        if (status.is5xxServerError()) {
            return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message);
        }
        return new ErrorCodeException(status, "MODEL_BAD_REQUEST", message);
    }
}

