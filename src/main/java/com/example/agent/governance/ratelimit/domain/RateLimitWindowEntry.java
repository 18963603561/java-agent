package com.example.agent.governance.ratelimit.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流窗口记录。
 */
public class RateLimitWindowEntry {

    /**
     * 当前分钟窗口。
     */
    private long minuteWindow;

    /**
     * 当前窗口计数。
     */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * 最近访问时间（毫秒）。
     */
    private volatile long lastAccessEpochMs = System.currentTimeMillis();

    public RateLimitWindowEntry(long minuteWindow) {
        this.minuteWindow = minuteWindow;
    }

    public long getMinuteWindow() {
        return minuteWindow;
    }

    public AtomicInteger getCount() {
        return count;
    }

    public long getLastAccessEpochMs() {
        return lastAccessEpochMs;
    }

    /**
     * 更新当前分钟窗口并重置计数。
     *
     * @param minuteWindow 分钟窗口
     */
    public void resetWindow(long minuteWindow) {
        this.minuteWindow = minuteWindow;
        this.count.set(0);
        touch();
    }

    /**
     * 更新最近访问时间。
     */
    public void touch() {
        this.lastAccessEpochMs = Instant.now().toEpochMilli();
    }
}

