package com.example.agent.gateway.controller;

import com.example.agent.common.TaskRequest;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.orchestrator.WorkflowRouter;
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
        "tenant.whitelist-paths=/actuator/health,/actuator/info",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
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
}
