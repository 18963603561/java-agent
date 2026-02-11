package com.example.agent.orchestration.multiagent.handoff;

/**
 * 交接状态枚举。
 *
 * <p>用途：统一交接生命周期状态，避免字符串状态造成的非法迁移与语义漂移。</p>
 */
public enum HandoffStatus {

    /**
     * 交接已创建，等待进入执行阶段。
     */
    PENDING,
    /**
     * 交接正在等待依赖数据或外部条件。
     */
    WAITING,
    /**
     * 交接已进入执行阶段。
     */
    RUNNING,
    /**
     * 交接执行成功。
     */
    SUCCEEDED,
    /**
     * 交接执行失败。
     */
    FAILED,
    /**
     * 交接被主动取消。
     */
    CANCELLED;

    /**
     * 判断是否终态。
     *
     * @return 是否为终态
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}

