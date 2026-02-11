package com.example.agent.gateway.controller;

import com.example.agent.AgentApplication;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import com.example.agent.orchestration.multiagent.dag.domain.port.DagDeadLetterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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
class DagDistributedOpsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DagDeadLetterRepository deadLetterRepository;

    @Test
    void shouldQueryMailboxOps() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/workflows/dag/ops/mailbox")
                        .queryParam("workflowId", "wf-ops")
                        .queryParam("dagRunId", "wf-ops:dag:1")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-ops")
                .header("X-Request-Id", "req-ops")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workflowId").isEqualTo("wf-ops")
                .jsonPath("$.data.dagRunId").isEqualTo("wf-ops:dag:1")
                .jsonPath("$.data.pendingCount").isEqualTo(0);
    }

    @Test
    void shouldQueryDeadLettersOps() {
        DagDeadLetterMessage deadLetter = new DagDeadLetterMessage();
        deadLetter.setDeadLetterId("dlq-ops-1");
        deadLetter.setDagRunId("wf-ops:dag:2");
        deadLetter.setWorkflowId("wf-ops");
        deadLetter.setNodeId("node-a");
        deadLetter.setReason("handler_error");
        deadLetterRepository.save(deadLetter);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/workflows/dag/ops/deadletters")
                        .queryParam("workflowId", "wf-ops")
                        .queryParam("dagRunId", "wf-ops:dag:2")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-ops")
                .header("X-Request-Id", "req-ops")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workflowId").isEqualTo("wf-ops")
                .jsonPath("$.data.dagRunId").isEqualTo("wf-ops:dag:2")
                .jsonPath("$.data.count").isEqualTo(1)
                .jsonPath("$.data.messages[0].deadLetterId").isEqualTo("dlq-ops-1");
    }
}
