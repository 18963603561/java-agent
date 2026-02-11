package com.example.agent.orchestration.multiagent.dag.audit;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagAuditRepositoryTest {

    @Test
    void shouldSaveAndQuerySnapshotByDagRunId() {
        InMemoryDagAuditRepository repository = new InMemoryDagAuditRepository();

        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-a:dag:1");
        runRecord.setWorkflowId("wf-a");
        runRecord.setStatus("RUNNING");
        runRecord.setStartedAt(Instant.now());
        repository.saveRunRecord(runRecord);

        DagNodeAttemptRecord attemptRecord = new DagNodeAttemptRecord();
        attemptRecord.setDagRunId("wf-a:dag:1");
        attemptRecord.setWorkflowId("wf-a");
        attemptRecord.setNodeId("n1");
        attemptRecord.setStatus("SUCCEEDED");
        repository.saveNodeAttempt(attemptRecord);

        DagDependencyEventRecord dependencyEventRecord = new DagDependencyEventRecord();
        dependencyEventRecord.setDagRunId("wf-a:dag:1");
        dependencyEventRecord.setWorkflowId("wf-a");
        dependencyEventRecord.setFromNode("n1");
        dependencyEventRecord.setToNode("n2");
        dependencyEventRecord.setDeliveryStatus("DELIVERED");
        repository.saveDependencyEvent(dependencyEventRecord);

        DagBackpressureRecord backpressureRecord = new DagBackpressureRecord();
        backpressureRecord.setDagRunId("wf-a:dag:1");
        backpressureRecord.setWorkflowId("wf-a");
        backpressureRecord.setNodeId("n2");
        backpressureRecord.setReason("mailbox_overloaded");
        repository.saveBackpressureRecord(backpressureRecord);

        DagAuditSnapshot snapshot = repository.findSnapshot("wf-a:dag:1").orElseThrow();
        assertEquals("wf-a", snapshot.getRunRecord().getWorkflowId());
        assertEquals(1, snapshot.getAttempts().size());
        assertEquals(1, snapshot.getDependencyEvents().size());
        assertEquals(1, snapshot.getBackpressureRecords().size());
    }

    @Test
    void shouldQueryRunRecordsByWorkflow() {
        InMemoryDagAuditRepository repository = new InMemoryDagAuditRepository();

        DagRunAuditRecord runRecordA = new DagRunAuditRecord();
        runRecordA.setDagRunId("wf-a:dag:1");
        runRecordA.setWorkflowId("wf-a");
        repository.saveRunRecord(runRecordA);

        DagRunAuditRecord runRecordB = new DagRunAuditRecord();
        runRecordB.setDagRunId("wf-b:dag:1");
        runRecordB.setWorkflowId("wf-b");
        repository.saveRunRecord(runRecordB);

        List<DagRunAuditRecord> runRecords = repository.findRunRecordsByWorkflow("wf-a");
        assertEquals(1, runRecords.size());
        assertEquals("wf-a:dag:1", runRecords.get(0).getDagRunId());
        assertTrue(repository.findRunRecordsByWorkflow("wf-c").isEmpty());
    }
}

