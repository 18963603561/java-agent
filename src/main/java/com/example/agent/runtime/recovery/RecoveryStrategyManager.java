package com.example.agent.runtime.recovery;

/**
 * 恢复策略管理器，根据失败类型与上下文条件选择策略。
 */
public class RecoveryStrategyManager {

    /**
     * 最大重试次数。
     */
    private final int maxRetries;

    /**
     * 最大分解次数。
     */
    private final int maxDecompose;

    public RecoveryStrategyManager(int maxRetries, int maxDecompose) {
        this.maxRetries = Math.max(0, maxRetries);
        this.maxDecompose = Math.max(0, maxDecompose);
    }

    /**
     * 选择恢复策略。
     *
     * @param failureType 失败类型
     * @param attempt 当前尝试次数
     * @param hasFallback 是否具备回退方案
     * @param decomposeAttempts 当前已分解次数
     * @return 恢复策略
     */
    public RecoveryStrategy select(FailureType failureType,
                                   int attempt,
                                   boolean hasFallback,
                                   int decomposeAttempts) {
        if (failureType == FailureType.RETRYABLE && attempt < maxRetries) {
            return RecoveryStrategy.RETRY;
        }
        if (failureType == FailureType.RETRYABLE && hasFallback) {
            return RecoveryStrategy.FALLBACK;
        }
        if (failureType == FailureType.DECOMPOSE && decomposeAttempts < maxDecompose) {
            return RecoveryStrategy.DECOMPOSE;
        }
        return RecoveryStrategy.STOP;
    }
}
