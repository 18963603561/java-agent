package com.example.agent.governance.ratelimit.domain;

/**
 * 限流决策对象。
 *
 * <p>用途：承载限流判定结果与上下文，避免仅返回布尔值导致语义丢失。</p>
 */
public class RateLimitDecision {

    private final boolean allowed;
    private final String reason;
    private final int currentCount;
    private final int maxPerMinute;

    public RateLimitDecision(boolean allowed, String reason, int currentCount, int maxPerMinute) {
        this.allowed = allowed;
        this.reason = reason;
        this.currentCount = currentCount;
        this.maxPerMinute = maxPerMinute;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxPerMinute() {
        return maxPerMinute;
    }
}

