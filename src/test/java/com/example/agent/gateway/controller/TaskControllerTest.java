package com.example.agent.gateway.controller;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.orchestration.task.TaskExecutionService;
import com.example.agent.orchestration.workflow.WorkflowRouter;
import com.example.agent.runtime.model.RuntimeResult;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "auth.api-keys.test-key.user-id=test-user",
        "auth.api-keys.test-key.roles=ROLE_USER",
        "auth.trusted-upstream.enabled=false",
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "agent.task.executor.core-pool-size=1",
        "agent.task.executor.max-pool-size=1",
        "agent.task.executor.queue-capacity=1",
        "agent.task.executor.keep-alive-seconds=30",
        "agent.task.sync.max-concurrency=1",
        "agent.memory.vector.enabled=false"
})
@AutoConfigureWebTestClient
class TaskControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TaskExecutionService taskExecutionService;

    @MockBean
    private WorkflowRouter workflowRouter;

    @MockBean
    private MetricsPublisher metricsPublisher;

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(workflowRouter, metricsPublisher);
    }

    @AfterEach
    void waitExecutorIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (taskExecutionService.getActiveCount() == 0 && taskExecutionService.getQueueSize() == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("任务执行器未在预期时间内空闲");
    }

    @Test
    void crossTenantAccessReturnsNotFound() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-tenant-a");

        AtomicReference<String> taskId = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.taskId").value(taskId::set);

        webTestClient.get()
                .uri("/api/v1/tasks/{taskId}", taskId.get())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-b")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void listTasksIsIsolatedByTenant() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-tenant-a-2");

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        webTestClient.get()
                .uri("/api/v1/tasks?size=10")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-b")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.tasks.length()").isEqualTo(0)
                .jsonPath("$.data.total").isEqualTo(0);
    }

    @Test
    void blankIdempotencyKeyCreatesNewTaskEachTime() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("   ");

        AtomicReference<String> firstTaskId = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.taskId").value(firstTaskId::set);

        TaskRequest secondRequest = new TaskRequest();
        secondRequest.setQuery("ping");
        secondRequest.setIdempotencyKey("   ");

        AtomicReference<String> secondTaskId = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(secondRequest)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.taskId").value(secondTaskId::set);

        assertThat(secondTaskId.get()).isNotEqualTo(firstTaskId.get());
    }

    @Test
    void idempotencyKeyReusesExistingTask() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-001");

        AtomicReference<String> firstTaskId = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.taskId").value(firstTaskId::set);

        TaskRequest secondRequest = new TaskRequest();
        secondRequest.setQuery("ping");
        secondRequest.setIdempotencyKey("idem-001");

        AtomicReference<String> secondTaskId = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(secondRequest)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.taskId").value(secondTaskId::set);

        assertThat(secondTaskId.get()).isEqualTo(firstTaskId.get());
    }

    @Test
    void submitTaskRunsInBackgroundThread() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
            return new RuntimeResult();
        }).when(workflowRouter).route(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("task-exec-");
    }

    @Test
    void submitTaskReturnsServiceUnavailableWhenQueueFull() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setQuery("slow-task");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            blocker.await(3, TimeUnit.SECONDS);
            finished.countDown();
            return new RuntimeResult();
        }).when(workflowRouter).route(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        TaskRequest second = new TaskRequest();
        second.setQuery("queue-task");
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(second)
                .exchange()
                .expectStatus().isAccepted();

        TaskRequest third = new TaskRequest();
        third.setQuery("reject-task");
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(third)
                .exchange()
                .expectStatus().isEqualTo(503);

        blocker.countDown();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void syncSubmitReturnsFinalResult() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setExecutionMode(TaskRequest.ExecutionMode.SYNC);
        request.setWaitTimeoutMs(1000L);

        org.mockito.Mockito.doAnswer(invocation -> {
            RuntimeResult result = new RuntimeResult();
            result.setFinalOutput(java.util.Map.of("answer", "ok"));
            return result;
        }).when(workflowRouter).route(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        AtomicReference<String> taskIdRef = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskId").value(taskIdRef::set)
                .jsonPath("$.data.status").isEqualTo("COMPLETED")
                .jsonPath("$.data.result.finalOutput.answer").isEqualTo("ok");

        webTestClient.get()
                .uri("/api/v1/tasks/{taskId}", taskIdRef.get())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.result.finalOutput.answer").isEqualTo("ok");
    }

    @Test
    void syncSubmitTimeoutFallsBackToAsync() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setQuery("slow");
        request.setExecutionMode(TaskRequest.ExecutionMode.SYNC);
        request.setWaitTimeoutMs(100L);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            blocker.await(1, TimeUnit.SECONDS);
            finished.countDown();
            RuntimeResult result = new RuntimeResult();
            result.setFinalOutput(java.util.Map.of("answer", "late"));
            return result;
        }).when(workflowRouter).route(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        AtomicReference<String> taskIdRef = new AtomicReference<>();
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SYNC_WAIT_TIMEOUT")
                .jsonPath("$.data.taskId").value(taskIdRef::set)
                .jsonPath("$.data.status").isEqualTo("RUNNING");

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        blocker.countDown();
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        awaitFinalAnswer(taskIdRef.get(), "late");
    }

    @Test
    void syncConcurrencyLimitDegradesToAsync() throws Exception {
        TaskRequest request = new TaskRequest();
        request.setQuery("block");
        request.setExecutionMode(TaskRequest.ExecutionMode.SYNC);
        request.setWaitTimeoutMs(3000L);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            blocker.await(1, TimeUnit.SECONDS);
            return new RuntimeResult();
        }).when(workflowRouter).route(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        java.util.concurrent.CompletableFuture<Void> first = java.util.concurrent.CompletableFuture.runAsync(() -> {
            webTestClient.post()
                    .uri("/api/v1/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", "test-key")
                    .header("X-Tenant-Id", "tenant-a")
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk();
        });

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        TaskRequest second = new TaskRequest();
        second.setQuery("degrade");
        second.setExecutionMode(TaskRequest.ExecutionMode.SYNC);
        second.setWaitTimeoutMs(3000L);

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(second)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("RUNNING");

        blocker.countDown();
        first.get(2, TimeUnit.SECONDS);
    }

    private void awaitFinalAnswer(String taskId, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                webTestClient.get()
                        .uri("/api/v1/tasks/{taskId}", taskId)
                        .header("X-API-Key", "test-key")
                        .header("X-Tenant-Id", "tenant-a")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.data.result.finalOutput.answer").isEqualTo(expected);
                return;
            } catch (AssertionError ex) {
                lastError = ex;
                Thread.sleep(50);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new AssertionError("等待任务结果超时");
    }
}
