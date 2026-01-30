package com.example.agent.gateway.controller;

import com.example.agent.common.TaskRequest;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.orchestrator.WorkflowRouter;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
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
        "tenant.whitelist-paths=/actuator/health,/actuator/info"
})
@AutoConfigureWebTestClient
class TaskControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private WorkflowRouter workflowRouter;

    @MockBean
    private MetricsPublisher metricsPublisher;

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
                .expectStatus().isOk()
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
                .expectStatus().isOk();

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
                .expectStatus().isOk()
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
                .expectStatus().isOk()
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
                .expectStatus().isOk()
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
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.taskId").value(secondTaskId::set);

        assertThat(secondTaskId.get()).isEqualTo(firstTaskId.get());
    }
}
