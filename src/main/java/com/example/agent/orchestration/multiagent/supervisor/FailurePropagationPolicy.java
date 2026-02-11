package com.example.agent.orchestration.multiagent.supervisor;

/**
 * 失败传播策略。
 */
public enum FailurePropagationPolicy {
    /**
     * 失败即中止。
     */
    FAIL_FAST,
    /**
     * 允许部分失败。
     */
    PARTIAL_SUCCESS
}

