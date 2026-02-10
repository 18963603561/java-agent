package com.example.agent.orchestration.task;

import org.springframework.stereotype.Component;

/**
 * 任务状态映射器。
 * <p>用途：统一任务状态在持久化文本与领域枚举之间的转换，避免字符串状态扩散。
 */
@Component
public class TaskStatusMapper {

    /**
     * 将文本状态转换为领域状态，非法状态直接抛出异常。
     */
    public TaskStatus toDomainStatus(String status) {
        return TaskStatus.require(status);
    }

    /**
     * 将领域状态转换为持久化文本。
     */
    public String toPersistedValue(TaskStatus status) {
        if (status == null) {
            return null;
        }
        return status.value();
    }
}

