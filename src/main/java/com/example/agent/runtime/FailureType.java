package com.example.agent.runtime;

/**
 * 失败类型枚举，用于驱动恢复策略。
 */
public enum FailureType {
    RETRYABLE,
    NON_RETRYABLE,
    DECOMPOSE
}
