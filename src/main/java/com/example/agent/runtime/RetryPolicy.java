package com.example.agent.runtime;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试退避策略，用于计算重试等待时间并控制抖动。
 */
public class RetryPolicy {

    /**
     * 基础退避毫秒数。
     */
    private final long baseDelayMillis;

    /**
     * 最大退避毫秒数。
     */
    private final long maxDelayMillis;

    /**
     * 抖动比例，范围 0~1。
     */
    private final double jitterRatio;

    public RetryPolicy(long baseDelayMillis, long maxDelayMillis, double jitterRatio) {
        this.baseDelayMillis = Math.max(0, baseDelayMillis);
        this.maxDelayMillis = Math.max(this.baseDelayMillis, maxDelayMillis);
        this.jitterRatio = Math.max(0, jitterRatio);
    }

    /**
     * 计算当前尝试的等待时间。
     *
     * @param attempt 尝试次数（从 1 开始）
     * @return 等待时长
     */
    public Duration nextDelay(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long factor = 1L << Math.min(attempt - 1, 10);
        long delay = baseDelayMillis * factor;
        if (delay > maxDelayMillis) {
            delay = maxDelayMillis;
        }
        if (jitterRatio > 0) {
            long jitter = (long) (delay * jitterRatio * ThreadLocalRandom.current().nextDouble());
            delay += jitter;
        }
        return Duration.ofMillis(delay);
    }

    /**
     * 依据重试等待时间进行阻塞等待。
     *
     * @param attempt 尝试次数
     */
    public void sleepBeforeRetry(int attempt) {
        Duration delay = nextDelay(attempt);
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
