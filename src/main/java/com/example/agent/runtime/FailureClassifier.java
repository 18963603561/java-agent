package com.example.agent.runtime;

import com.example.agent.common.ErrorCodeProvider;

/**
 * 失败分类器，用于为重试与降级策略提供依据。
 */
public class FailureClassifier {

    /**
     * 分类失败类型。
     *
     * @param throwable 异常
     * @return 失败类型
     */
    public FailureType classify(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            String code = provider.getErrorCode();
            if (isRetryable(code)) {
                return FailureType.RETRYABLE;
            }
            if (isDecompose(code)) {
                return FailureType.DECOMPOSE;
            }
            if (isNonRetryable(code)) {
                return FailureType.NON_RETRYABLE;
            }
        }
        return FailureType.NON_RETRYABLE;
    }

    private boolean isRetryable(String code) {
        return "MCP_UNAVAILABLE".equals(code)
                || "CIRCUIT_OPEN".equals(code)
                || "RATE_LIMITED".equals(code)
                || "EVENT_PERSIST_FAILED".equals(code)
                || "STREAM_TIMEOUT".equals(code);
    }

    private boolean isDecompose(String code) {
        return "BUDGET_EXCEEDED".equals(code);
    }

    private boolean isNonRetryable(String code) {
        return "HOOK_BLOCKED".equals(code)
                || "POLICY_DENIED".equals(code)
                || "SANDBOX_DENIED".equals(code)
                || "INVALID_REQUEST".equals(code)
                || "NOT_FOUND".equals(code)
                || "CANCELLED".equals(code)
                || "EXECUTION_INTERRUPTED".equals(code);
    }
}
