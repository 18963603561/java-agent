package com.example.agent.orchestration.multiagent.dag.diagnosis;

import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagBackpressureRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagNodeAttemptRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import com.example.agent.orchestration.multiagent.dag.infrastructure.audit.InMemoryDagAuditRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DagDiagnosisServiceTest {

    @Test
    void shouldGenerateDiagnosisReportWithHotspotsAndDistributions() {
        InMemoryDagAuditRepository auditRepository = new InMemoryDagAuditRepository();
        DagAuditService auditService = new DagAuditService(auditRepository);
        DagDiagnosisService diagnosisService = new DagDiagnosisService(auditService);

        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-dia:dag:1");
        runRecord.setWorkflowId("wf-dia");
        runRecord.setStatus("FAILED");
        runRecord.setFailurePolicy("PARTIAL_SUCCESS");
        runRecord.setStartedAt(Instant.now());
        auditRepository.saveRunRecord(runRecord);

        DagNodeAttemptRecord attempt1 = new DagNodeAttemptRecord();
        attempt1.setDagRunId("wf-dia:dag:1");
        attempt1.setWorkflowId("wf-dia");
        attempt1.setNodeId("node-a");
        attempt1.setStatus("RETRYING");
        attempt1.setDurationMs(100L);
        auditRepository.saveNodeAttempt(attempt1);

        DagNodeAttemptRecord attempt2 = new DagNodeAttemptRecord();
        attempt2.setDagRunId("wf-dia:dag:1");
        attempt2.setWorkflowId("wf-dia");
        attempt2.setNodeId("node-a");
        attempt2.setStatus("FAILED");
        attempt2.setReasonCode("NODE_FAILED");
        attempt2.setDurationMs(120L);
        auditRepository.saveNodeAttempt(attempt2);

        DagBackpressureRecord backpressureRecord = new DagBackpressureRecord();
        backpressureRecord.setDagRunId("wf-dia:dag:1");
        backpressureRecord.setWorkflowId("wf-dia");
        backpressureRecord.setNodeId("node-b");
        backpressureRecord.setReason("mailbox_overloaded");
        backpressureRecord.setDelayMs(200L);
        auditRepository.saveBackpressureRecord(backpressureRecord);

        DagDiagnosisReport report = diagnosisService.diagnose("wf-dia", "wf-dia:dag:1");

        assertEquals("wf-dia", report.getWorkflowId());
        assertEquals("wf-dia:dag:1", report.getDagRunId());
        assertEquals("node-a", report.getRetryHotspotNodeId());
        assertEquals("node-b", report.getBackpressureHotspotNodeId());
        assertEquals(1, report.getFailureReasonDistribution().get("NODE_FAILED"));
        assertEquals(1, report.getBlockingReasonDistribution().get("MAILBOX_OVERLOADED"));
        assertFalse(report.getRecommendations().isEmpty());
    }
}
