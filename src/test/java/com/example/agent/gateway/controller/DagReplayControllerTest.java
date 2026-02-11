package com.example.agent.gateway.controller;

import com.example.agent.AgentApplication;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import java.time.Instant;
import java.util.Map;
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
class DagReplayControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DagAuditService dagAuditService;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Test
    void shouldQueryDagReplayFrames() {
        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-replay-api:dag:1");
        runRecord.setWorkflowId("wf-replay-api");
        runRecord.setStatus("COMPLETED");
        runRecord.setStartedAt(Instant.now());
        dagAuditService.saveRunRecord(runRecord);

        EventLogRecord event = new EventLogRecord();
        event.setEventId("wf-replay-api:1");
        event.setWorkflowId("wf-replay-api");
        event.setTenantId("tenant-a");
        event.setType("STEP_STARTED");
        event.setTimestamp(Instant.now());
        event.setPayload(Map.of("dagRunId", "wf-replay-api:dag:1", "nodeId", "planner", "attempt", 1));
        eventLogRepository.saveIfAbsent(event);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/workflows/dag/replay")
                        .queryParam("workflowId", "wf-replay-api")
                        .queryParam("dagRunId", "wf-replay-api:dag:1")
                        .queryParam("size", 20)
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-replay")
                .header("X-Request-Id", "req-replay")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.dagRunId").isEqualTo("wf-replay-api:dag:1")
                .jsonPath("$.data.frames.length()").isEqualTo(1)
                .jsonPath("$.data.frames[0].nodeId").isEqualTo("planner");
    }
}

