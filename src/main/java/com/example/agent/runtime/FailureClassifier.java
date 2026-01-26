package com.example.agent.runtime;

import com.example.agent.common.ErrorCodeProvider;

/**
 * 失败分类器，根据异常信息输出失败类型。
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
            if ("MCP_UNAVAILABLE".equals(code) || "CIRCUIT_OPEN".equals(code)) {
                return FailureType.RETRYABLE;
            }
            if ("HOOK_BLOCKED".equals(code) || "POLICY_DENIED".equals(code)) {
                return FailureType.NON_RETRYABLE;
            }
        }
        return FailureType.NON_RETRYABLE;
    }
}
