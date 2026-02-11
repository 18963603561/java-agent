package com.example.agent.gateway.controller;

import com.example.agent.AgentApplication;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagBackpressureRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagNodeAttemptRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import java.time.Instant;
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
class DagDiagnosisControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DagAuditService dagAuditService;

    @Test
    void shouldQueryDagDiagnosisReport() {
        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-diagnosis-api:dag:1");
        runRecord.setWorkflowId("wf-diagnosis-api");
        runRecord.setStatus("FAILED");
        runRecord.setFailurePolicy("PARTIAL_SUCCESS");
        runRecord.setStartedAt(Instant.now());
        dagAuditService.saveRunRecord(runRecord);

        DagNodeAttemptRecord attemptRecord = new DagNodeAttemptRecord();
        attemptRecord.setDagRunId("wf-diagnosis-api:dag:1");
        attemptRecord.setWorkflowId("wf-diagnosis-api");
        attemptRecord.setNodeId("node-a");
        attemptRecord.setStatus("FAILED");
        attemptRecord.setReasonCode("NODE_FAILED");
        attemptRecord.setDurationMs(200L);
        dagAuditService.saveNodeAttempt(attemptRecord);

        DagBackpressureRecord backpressureRecord = new DagBackpressureRecord();
        backpressureRecord.setDagRunId("wf-diagnosis-api:dag:1");
        backpressureRecord.setWorkflowId("wf-diagnosis-api");
        backpressureRecord.setNodeId("node-b");
        backpressureRecord.setReason("mailbox_overloaded");
        backpressureRecord.setDelayMs(300L);
        dagAuditService.saveBackpressureRecord(backpressureRecord);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/workflows/dag/diagnosis")
                        .queryParam("workflowId", "wf-diagnosis-api")
                        .queryParam("dagRunId", "wf-diagnosis-api:dag:1")
                        .build())
                .header("X-API-Key", "test-key")
                .header("X-Tenant-Id", "tenant-a")
                .header("X-Trace-Id", "trace-diagnosis")
                .header("X-Request-Id", "req-diagnosis")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.dagRunId").isEqualTo("wf-diagnosis-api:dag:1")
                .jsonPath("$.data.status").isEqualTo("FAILED")
                .jsonPath("$.data.failureReasonDistribution.NODE_FAILED").isEqualTo(1);
    }
}

