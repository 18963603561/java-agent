package com.example.agent.orchestration.multiagent.dag.actor.control;

import org.springframework.util.StringUtils;

/**
 * 控制命令类型。
 * <p>用途：统一 DAG 控制命令字典，避免字符串分支漂移。</p>
 */
public enum ControlCommandType {

    /**
     * 暂停。
     */
    PAUSE,

    /**
     * 恢复。
     */
    RESUME,

    /**
     * 重平衡。
     */
    REBALANCE,

    /**
     * 故障恢复。
     */
    RECOVER,

    /**
     * 重放死信。
     */
    REPLAY_DEADLETTER;

    /**
     * 解析命令类型。
     */
    public static ControlCommandType parse(String command) {
        if (!StringUtils.hasText(command)) {
            return null;
        }
        String normalized = command.trim().toUpperCase().replace('-', '_');
        try {
            return ControlCommandType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

