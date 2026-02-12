package com.example.agent.capabilities.context.compression.domain.model;

import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 历史窗口整形命令。
 *
 * <p>用途：统一承载三段式滑窗整形入参，隔离调用方与策略实现细节。</p>
 */
public class HistoryWindowShapeCommand {

    /**
     * 原始上下文快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 首段保留条数。
     */
    private int primersCount;

    /**
     * 尾段保留条数。
     */
    private int recentsCount;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public int getPrimersCount() {
        return primersCount;
    }

    public void setPrimersCount(int primersCount) {
        this.primersCount = primersCount;
    }

    public int getRecentsCount() {
        return recentsCount;
    }

    public void setRecentsCount(int recentsCount) {
        this.recentsCount = recentsCount;
    }
}

