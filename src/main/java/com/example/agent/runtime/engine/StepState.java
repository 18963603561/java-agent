package com.example.agent.runtime.engine;

/**
 * 步骤状态枚举，用于描述运行时步骤生命周期。
 */
public enum StepState {
    PENDING,
    STARTED,
    COMPLETED,
    FAILED,
    SKIPPED,
    WAITING
}
