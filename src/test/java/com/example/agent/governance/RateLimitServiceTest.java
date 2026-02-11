package com.example.agent.governance;

import com.example.agent.governance.ratelimit.domain.RateLimitStore;
import com.example.agent.governance.ratelimit.domain.RateLimitDecision;
import com.example.agent.governance.ratelimit.RateLimitService;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.util.Map;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流服务测试。
 */
class RateLimitServiceTest {

    @Test
    void allowShouldRejectAfterThreshold() {
        RateLimitService service = buildService(2, 100, 3600, 1);

        assertTrue(service.allow("tenant-a:tool-a"));
        assertTrue(service.allow("tenant-a:tool-a"));
        assertFalse(service.allow("tenant-a:tool-a"));
    }

    @Test
    void cleanupShouldRemoveExpiredKeys() throws InterruptedException {
        RateLimitService service = buildService(10, 100, 1, 1);
        service.allow("tenant-a:tool-a");
        assertEquals(1, service.cachedKeyCount());

        Thread.sleep(1200);
        service.allow("tenant-a:tool-b");

        assertEquals(1, service.cachedKeyCount());
    }

    @Test
    void capacityExceededShouldRejectNewKey() {
        RateLimitService service = buildService(10, 1, 3600, 1);
        assertTrue(service.allow("tenant-a:tool-a"));
        assertFalse(service.allow("tenant-a:tool-b"));
    }

    @Test
    void allowShouldRespectThresholdUnderConcurrency() throws InterruptedException {
        avoidMinuteBoundary(3);
        RateLimitService service = buildService(50, 100, 3600, 1);
        String key = "tenant-a:tool-concurrent";
        int totalRequests = 200;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalRequests);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int index = 0; index < totalRequests; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (service.allow(key)) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(50, allowed.get());
        assertEquals(150, rejected.get());
    }

    @Test
    void allowShouldResetCounterWhenWindowDrifts() {
        RateLimitService service = buildService(2, 100, 3600, 1);
        String key = "tenant-a:tool-clock";
        assertTrue(service.allow(key));
        assertTrue(service.allow(key));
        assertFalse(service.allow(key));

        RateLimitStore store = (RateLimitStore) ReflectionTestUtils.getField(service, "rateLimitStore");
        Object stateStore = ReflectionTestUtils.getField(store, "stateStore");
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = (Map<String, Object>) ReflectionTestUtils.getField(stateStore, "entries");
        Object entry = entries.get(key);
        long currentMinuteWindow = Instant.now().getEpochSecond() / 60;
        ReflectionTestUtils.setField(entry, "minuteWindow", currentMinuteWindow - 1);

        // 该断言验证分钟窗口漂移后，计数会重置并重新放行。
        assertTrue(service.allow(key));
    }

    @Test
    void evaluateShouldReturnStructuredDecision() {
        RateLimitService service = buildService(1, 100, 3600, 1);
        String key = "tenant-a:tool-decision";

        RateLimitDecision first = service.evaluate(key);
        RateLimitDecision second = service.evaluate(key);

        assertTrue(first.isAllowed());
        assertEquals("allowed", first.getReason());
        assertFalse(second.isAllowed());
        assertEquals("threshold", second.getReason());
        assertEquals(1, second.getMaxPerMinute());
    }

    private void avoidMinuteBoundary(int guardSeconds) throws InterruptedException {
        long offset = Instant.now().getEpochSecond() % 60;
        if (offset >= 60 - guardSeconds) {
            Thread.sleep((guardSeconds + 1L) * 1000L);
        }
    }

    private RateLimitService buildService(int maxPerMinute, int maxKeys, int ttlSeconds, int cleanupSeconds) {
        MetricsPublisher publisher = new MetricsPublisher(new SimpleMeterRegistry());
        RateLimitService service = new RateLimitService(
                publisher,
                new RateLimitStore(publisher),
                new GovernanceTelemetry(publisher));
        ReflectionTestUtils.setField(service, "maxPerMinute", maxPerMinute);
        ReflectionTestUtils.setField(service, "maxKeys", maxKeys);
        ReflectionTestUtils.setField(service, "entryTtlSeconds", ttlSeconds);
        ReflectionTestUtils.setField(service, "cleanupIntervalSeconds", cleanupSeconds);
        return service;
    }
}
