package com.example.agent.gateway.controller;

import com.example.agent.AgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes = AgentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "auth.api-keys.test-key.user-id=test-user",
                "auth.api-keys.test-key.roles=ROLE_USER",
                "auth.trusted-upstream.enabled=false",
                "tenant.whitelist-paths=/actuator/health,/actuator/info",
                "agent.memory.vector.enabled=false"
        })
@AutoConfigureWebTestClient
class DagControlControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldExecutePauseCommand() {
        String requestBody = """
                {
                  "workflowId": "wf-control-api",
                  "dagRunId": "wf-control-api:dag:1",
                  "command": "pause",
                  "idempotencyKey": "pause-1"
                }
                """;

        webTestClient.post()
                .uri("/api/v1/workflows/dag/control")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-control")
                .header("X-Request-Id", "req-control")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workflowId").isEqualTo("wf-control-api")
                .jsonPath("$.data.dagRunId").isEqualTo("wf-control-api:dag:1")
                .jsonPath("$.data.command").isEqualTo("PAUSE")
                .jsonPath("$.data.status").isEqualTo("SUCCESS")
                .jsonPath("$.data.deduplicated").isEqualTo(false);
    }

    @Test
    void shouldReturnDeduplicatedForSameIdempotencyKey() {
        String requestBody = """
                {
                  "workflowId": "wf-control-api",
                  "dagRunId": "wf-control-api:dag:2",
                  "command": "resume",
                  "idempotencyKey": "resume-dup-1"
                }
                """;

        webTestClient.post()
                .uri("/api/v1/workflows/dag/control")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-control")
                .header("X-Request-Id", "req-control")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/v1/workflows/dag/control")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-control")
                .header("X-Request-Id", "req-control")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.command").isEqualTo("RESUME")
                .jsonPath("$.data.status").isEqualTo("SUCCESS")
                .jsonPath("$.data.deduplicated").isEqualTo(true);
    }
}
