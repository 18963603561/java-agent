package com.example.agent.orchestrator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.orchestration.task.TaskExecutionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void shutdownCompletesWithoutLeak() throws Exception {
        TaskExecutionService service = buildService();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);

        var future = service.submit("task-shutdown", () -> {
            started.countDown();
            try {
                blocker.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));

        blocker.countDown();
        service.shutdown();

        assertTrue(future.isDone());
        assertEquals(0, service.getActiveCount());
    }

    @Test
    void shutdownInterruptsLongRunningTaskWhenThreadInterrupted() throws Exception {
        TaskExecutionService service = buildService();
        AtomicReference<Thread> taskThread = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        var future = service.submit("task-force-stop", () -> {
            taskThread.set(Thread.currentThread());
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.yield();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertNotNull(taskThread.get());
        taskThread.get().interrupt();

        service.shutdown();
        future.get(2, TimeUnit.SECONDS);
        assertEquals(0, service.getActiveCount());
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
