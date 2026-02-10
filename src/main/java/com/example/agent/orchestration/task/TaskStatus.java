package com.example.agent.orchestration.task;

import org.springframework.util.StringUtils;

/**
 * 任务状态枚举。
 * <p>用途：收敛任务生命周期状态，避免硬编码字符串分散。
 */
public enum TaskStatus {

    /**
     * 已提交。
     */
    SUBMITTED,

    /**
     * 运行中。
     */
    RUNNING,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 执行失败。
     */
    FAILED;

    /**
     * 判断是否终态。
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * 转换为持久化字符串。
     */
    public String value() {
        return name();
    }

    /**
     * 从字符串解析状态。
     */
    public static TaskStatus from(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 将状态字符串转换为受控值，非法状态直接失败。
     */
    public static TaskStatus require(String status) {
        TaskStatus parsed = from(status);
        if (parsed != null) {
            return parsed;
        }
        throw new IllegalArgumentException("invalid_task_status");
    }
}
