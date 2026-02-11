package com.example.agent.governance.circuitbreaker.domain;

/**
 * 熔断决策对象。
 *
 * <p>用途：输出当前熔断状态与决策原因，供调用方做事件与异常映射。</p>
 */
public class CircuitDecision {

    private final boolean allowed;
    private final String reason;
    private final String state;
    private final long openedAtEpochSeconds;
    private final int openSeconds;

    public CircuitDecision(boolean allowed,
                           String reason,
                           String state,
                           long openedAtEpochSeconds,
                           int openSeconds) {
        this.allowed = allowed;
        this.reason = reason;
        this.state = state;
        this.openedAtEpochSeconds = openedAtEpochSeconds;
        this.openSeconds = openSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getState() {
        return state;
    }

    public long getOpenedAtEpochSeconds() {
        return openedAtEpochSeconds;
    }

    public int getOpenSeconds() {
        return openSeconds;
    }
}

