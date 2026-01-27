package com.example.agent.runtime;

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

    public boolean shouldStop() {
        return shouldStop;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getReason() {
        return reason;
    }
}
