package com.example.agent.governance;

/**
 * 限流规则。
 */
public class RateLimitRule {

    private int maxPerMinute;

    public RateLimitRule() {
    }

    public RateLimitRule(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    public int getMaxPerMinute() {
        return maxPerMinute;
    }

    public void setMaxPerMinute(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }
}
