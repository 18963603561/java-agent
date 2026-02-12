package com.example.agent.capabilities.context.compression.domain.model;

import com.example.agent.capabilities.context.model.ContextSnapshot;

/**
 * 历史窗口整形结果。
 *
 * <p>用途：统一表达三段式滑窗整形产物与元数据，支撑后续编排、观测与审计。</p>
 */
public class HistoryWindowShapeResult {

    /**
     * 整形后快照。
     */
    private ContextSnapshot snapshot;

    /**
     * 是否执行窗口整形。
     */
    private boolean windowShaped;

    /**
     * 窗口整形原因。
     */
    private String shapeReason;

    /**
     * 首段保留条数。
     */
    private int primersRetained;

    /**
     * 尾段保留条数。
     */
    private int recentsRetained;

    /**
     * 中段窗口条数。
     */
    private int middleWindowSize;

    public ContextSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ContextSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public boolean isWindowShaped() {
        return windowShaped;
    }

    public void setWindowShaped(boolean windowShaped) {
        this.windowShaped = windowShaped;
    }

    public String getShapeReason() {
        return shapeReason;
    }

    public void setShapeReason(String shapeReason) {
        this.shapeReason = shapeReason;
    }

    public int getPrimersRetained() {
        return primersRetained;
    }

    public void setPrimersRetained(int primersRetained) {
        this.primersRetained = primersRetained;
    }

    public int getRecentsRetained() {
        return recentsRetained;
    }

    public void setRecentsRetained(int recentsRetained) {
        this.recentsRetained = recentsRetained;
    }

    public int getMiddleWindowSize() {
        return middleWindowSize;
    }

    public void setMiddleWindowSize(int middleWindowSize) {
        this.middleWindowSize = middleWindowSize;
    }
}

