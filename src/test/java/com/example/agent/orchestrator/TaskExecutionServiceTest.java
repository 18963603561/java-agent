package com.example.agent.orchestrator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutionServiceTest {

    @Test
    void submitCompletionCleansMapping() throws Exception {
        TaskExecutionService service = buildService();
        AtomicInteger runs = new AtomicInteger(0);

        service.submit("task-clean", runs::incrementAndGet).get(2, TimeUnit.SECONDS);
        service.submit("task-clean", runs::incrementAndGet).get(2, TimeUnit.SECONDS);

        assertEquals(2, runs.get());
    }

    @Test
    void duplicateSubmitDoesNotExecuteTwice() throws Exception {
        TaskExecutionService service = buildService();
        AtomicInteger runs = new AtomicInteger(0);
        CountDownLatch blocker = new CountDownLatch(1);

        var first = service.submit("task-dup", () -> {
            runs.incrementAndGet();
            try {
                blocker.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        var second = service.submit("task-dup", runs::incrementAndGet);

        assertSame(first, second);
        blocker.countDown();
        assertTrue(first.get(2, TimeUnit.SECONDS) == null);
        assertEquals(1, runs.get());
    }

    private TaskExecutionService buildService() {
        TaskExecutionService service = new TaskExecutionService();
        ReflectionTestUtils.setField(service, "corePoolSize", 1);
        ReflectionTestUtils.setField(service, "maxPoolSize", 1);
        ReflectionTestUtils.setField(service, "queueCapacity", 4);
        ReflectionTestUtils.setField(service, "keepAliveSeconds", 5L);
        ReflectionTestUtils.setField(service, "threadNamePrefix", "task-exec-test-");
        service.init();
        return service;
    }
}
