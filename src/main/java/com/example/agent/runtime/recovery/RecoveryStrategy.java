package com.example.agent.runtime.recovery;

/**
 * 恢复策略枚举。
 */
public enum RecoveryStrategy {
    RETRY,
    FALLBACK,
    DECOMPOSE,
    STOP
}
