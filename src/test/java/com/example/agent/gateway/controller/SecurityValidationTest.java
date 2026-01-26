package com.example.agent.gateway.controller;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.common.TaskResponse;
import com.example.agent.orchestrator.TaskQueryService;
import com.example.agent.orchestrator.TaskSubmissionService;
import com.example.agent.streaming.EventStreamService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class SecurityValidationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TaskSubmissionService taskSubmissionService;

    @MockBean
    private TaskQueryService taskQueryService;

    @MockBean
    private EventStreamService eventStreamService;

    @Test
    void missingApiKeyReturnsUnauthorized() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-1");

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidApiKeyReturnsForbidden() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-2");

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .header("X-API-Key", "invalid-key")
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void forgedUserHeadersAreIgnoredByDefault() {
        TaskRequest request = new TaskRequest();
        request.setQuery("ping");
        request.setIdempotencyKey("idem-3");
        when(taskSubmissionService.submitTask(any(), any()))
                .thenReturn(new TaskResponse("task-1", "wf-1", "SUBMITTED"));

        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "tenant-a")
                .header("X-API-Key", "test-key")
                .header("X-User-Id", "evil-user")
                .header("X-Roles", "admin")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<TenantContext> captor = ArgumentCaptor.forClass(TenantContext.class);
        verify(taskSubmissionService, times(1)).submitTask(any(), captor.capture());
        TenantContext tenantContext = captor.getValue();
        assertEquals("test-user", tenantContext.getUserId());
        assertTrue(tenantContext.getRoles().contains("ROLE_USER"));
    }

    @Test
    void missingTenantIdReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks?size=1")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_MISSING");
    }

    @Test
    void healthEndpointBypassesTenantCheck() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void sizeMissingReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void sizeOutOfRangeReturnsBadRequest() {
        webTestClient.get()
                .uri("/api/v1/tasks?size=1000")
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}
