package com.example.agent.reflection;

/**
 * 反思决策状态。
 *
 * <p>用途：描述一次反思策略执行后的流程走向，避免使用空值表达流程分支。</p>
 */
public enum ReflectionDecisionStatus {

    /**
     * 反思已完成，返回可消费的反思结果。
     */
    COMPLETED,

    /**
     * 当前策略失败，建议进入下一策略（通常为规则兜底）。
     */
    FALLBACK_REQUIRED,

    /**
     * 反思失败且不可继续。
     */
    TERMINAL_FAILURE
}

