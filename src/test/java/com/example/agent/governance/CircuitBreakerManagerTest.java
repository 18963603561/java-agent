package com.example.agent.governance;

import com.example.agent.governance.circuitbreaker.CircuitBreakerManager;
import com.example.agent.governance.circuitbreaker.domain.CircuitStateStore;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 熔断管理器测试。
 */
class CircuitBreakerManagerTest {

    @Test
    void shouldOpenAfterThresholdAndRecoverAfterWindow() throws InterruptedException {
        CircuitBreakerManager manager = buildManager(2, 1, 100, 3600, 1);

        manager.recordFailure("tenant-a:tool-a");
        manager.recordFailure("tenant-a:tool-a");

        assertFalse(manager.allow("tenant-a:tool-a"));

        Thread.sleep(1200);
        assertTrue(manager.allow("tenant-a:tool-a"));
    }

    @Test
    void shouldCleanupExpiredStates() throws InterruptedException {
        CircuitBreakerManager manager = buildManager(2, 10, 100, 1, 1);

        manager.recordFailure("tenant-a:tool-a");
        assertEquals(1, manager.stateSize());

        Thread.sleep(1200);
        manager.stateSize();

        assertEquals(0, manager.stateSize());
    }

    @Test
    void newKeyShouldBeRejectedWhenCapacityExceeded() {
        CircuitBreakerManager manager = buildManager(2, 10, 1, 3600, 1);
        manager.recordFailure("tenant-a:tool-a");

        assertFalse(manager.allow("tenant-a:tool-b"));
    }

    @Test
    void concurrentRecordFailureShouldOpenCircuit() throws InterruptedException {
        CircuitBreakerManager manager = buildManager(20, 30, 100, 3600, 1);
        String key = "tenant-a:tool-concurrency";
        int totalFailures = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalFailures);
        for (int index = 0; index < totalFailures; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                    manager.recordFailure(key);
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

        assertFalse(manager.allow(key));
    }

    @Test
    void concurrentAllowShouldRejectWhenCircuitOpen() throws InterruptedException {
        CircuitBreakerManager manager = buildManager(1, 30, 100, 3600, 1);
        String key = "tenant-a:tool-open";
        manager.recordFailure(key);

        int totalAttempts = 80;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalAttempts);
        AtomicInteger rejected = new AtomicInteger();
        for (int index = 0; index < totalAttempts; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (!manager.allow(key)) {
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

        assertEquals(totalAttempts, rejected.get());
    }

    private CircuitBreakerManager buildManager(int failureThreshold,
                                               int openSeconds,
                                               int maxKeys,
                                               int stateTtlSeconds,
                                               int cleanupIntervalSeconds) {
        MetricsPublisher publisher = new MetricsPublisher(new SimpleMeterRegistry());
        CircuitBreakerManager manager = new CircuitBreakerManager(
                publisher,
                new CircuitStateStore(publisher),
                new GovernanceTelemetry(publisher));
        ReflectionTestUtils.setField(manager, "failureThreshold", failureThreshold);
        ReflectionTestUtils.setField(manager, "openSeconds", openSeconds);
        ReflectionTestUtils.setField(manager, "maxKeys", maxKeys);
        ReflectionTestUtils.setField(manager, "stateTtlSeconds", stateTtlSeconds);
        ReflectionTestUtils.setField(manager, "cleanupIntervalSeconds", cleanupIntervalSeconds);
        return manager;
    }
}
