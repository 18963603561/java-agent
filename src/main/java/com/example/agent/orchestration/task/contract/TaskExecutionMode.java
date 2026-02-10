package com.example.agent.orchestration.task.contract;

/**
 * 任务执行模式。
 * <p>ASYNC 表示异步提交后立即返回，SYNC 表示尝试同步等待任务结果。
 */
public enum TaskExecutionMode {

    /**
     * 异步执行模式。
     */
    ASYNC,

    /**
     * 同步等待模式。
     */
    SYNC
}

