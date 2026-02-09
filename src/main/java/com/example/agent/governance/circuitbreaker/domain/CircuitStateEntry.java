package com.example.agent.governance.circuitbreaker.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断状态记录。
 */
public class CircuitStateEntry {

    /**
     * 连续失败次数。
     */
    private final AtomicInteger failures = new AtomicInteger();

    /**
     * 是否处于打开状态。
     */
    private volatile boolean open;

    /**
     * 打开时间（秒）。
     */
    private volatile long openedAtEpochSeconds;

    /**
     * 最近访问时间（毫秒）。
     */
    private volatile long lastAccessEpochMs = System.currentTimeMillis();

    public AtomicInteger getFailures() {
        return failures;
    }

    public boolean isOpen() {
        return open;
    }

    public long getOpenedAtEpochSeconds() {
        return openedAtEpochSeconds;
    }

    public long getLastAccessEpochMs() {
        return lastAccessEpochMs;
    }

    /**
     * 标记访问。
     */
    public void touch() {
        this.lastAccessEpochMs = Instant.now().toEpochMilli();
    }

    /**
     * 打开熔断状态。
     */
    public void openNow() {
        this.open = true;
        this.openedAtEpochSeconds = Instant.now().getEpochSecond();
        touch();
    }

    /**
     * 关闭熔断状态并清空失败次数。
     */
    public void closeAndReset() {
        this.open = false;
        this.failures.set(0);
        touch();
    }
}

