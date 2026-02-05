package com.example.agent.runtime.control;

/**
 * 执行控制状态枚举，用于描述工作流运行态。
 */
public enum ExecutionControlState {
    RUNNING,
    PAUSED,
    CANCELLED,
    WAIT_APPROVAL
}
