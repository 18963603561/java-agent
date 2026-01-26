package com.example.agent.runtime;

/**
 * 恢复策略管理器，根据失败类型与重试次数决定策略。
 */
public class RecoveryStrategyManager {

    private final int maxRetries;

    public RecoveryStrategyManager(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * 选择恢复策略。
     *
     * @param failureType 失败类型
     * @param attempt 当前尝试次数
     * @return 恢复策略
     */
    public RecoveryStrategy select(FailureType failureType, int attempt) {
        if (failureType == FailureType.RETRYABLE && attempt < maxRetries) {
            return RecoveryStrategy.RETRY;
        }
        if (failureType == FailureType.DECOMPOSE) {
            return RecoveryStrategy.DECOMPOSE;
        }
        return RecoveryStrategy.STOP;
    }
}
