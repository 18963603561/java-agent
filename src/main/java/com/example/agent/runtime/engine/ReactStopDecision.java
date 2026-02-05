package com.example.agent.runtime.engine;

/**
 * ReAct 终止判断结果。
 */
public class ReactStopDecision {

    private final boolean shouldStop;
    private final boolean completed;
    private final String reason;

    public ReactStopDecision(boolean shouldStop, boolean completed, String reason) {
        this.shouldStop = shouldStop;
        this.completed = completed;
        this.reason = reason;
    }

    /**
     * 判断是否需要停止循环。
     *
     * @return 是否需要停止
     */
    public boolean shouldStop() {
        return shouldStop;
    }

    /**
     * 判断是否已经完成目标。
     *
     * @return 是否完成
     */
    public boolean isCompleted() {
        return completed;
    }

    public String getReason() {
        return reason;
    }
}
