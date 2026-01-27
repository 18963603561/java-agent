package com.example.agent.tools.hook;

/**
 * Hook 超时策略。
 */
public enum HookTimeoutPolicy {
    /**
     * 超时放行。
     */
    FAIL_OPEN,
    /**
     * 超时阻断。
     */
    FAIL_CLOSED
}
