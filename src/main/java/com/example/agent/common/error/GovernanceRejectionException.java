package com.example.agent.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 治理拒绝异常。
 *
 * <p>用途：在限流、熔断、背压场景下携带结构化上下文，供上层事件发布使用。</p>
 */
public class GovernanceRejectionException extends ErrorCodeException {

    /**
     * 治理上下文。
     */
    private final Map<String, Object> context;

    public GovernanceRejectionException(HttpStatus status,
                                        String errorCode,
                                        String reason,
                                        Map<String, Object> context) {
        super(status, errorCode, reason);
        if (context == null || context.isEmpty()) {
            this.context = Map.of();
        } else {
            this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
        }
    }

    public Map<String, Object> getContext() {
        return context;
    }
}

