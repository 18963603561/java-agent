package com.example.agent.governance;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 简易限流服务，按分钟窗口限制调用频次。
 */
@Service
public class RateLimitService {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${agent.rate-limit.max-per-minute:100}")
    private int maxPerMinute;

    public boolean allow(String key) {
        long currentWindow = Instant.now().getEpochSecond() / 60;
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(currentWindow));
        synchronized (counter) {
            if (counter.window != currentWindow) {
                counter.window = currentWindow;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() <= maxPerMinute;
        }
    }

    private static class WindowCounter {
        private long window;
        private final AtomicInteger count = new AtomicInteger();

        private WindowCounter(long window) {
            this.window = window;
        }
    }
}
