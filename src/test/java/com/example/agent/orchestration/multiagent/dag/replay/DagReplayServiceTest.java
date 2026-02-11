package com.example.agent.orchestration.multiagent.dag.replay;

import com.example.agent.history.eventlog.InMemoryEventLogRepository;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagRunAuditRecord;
import com.example.agent.orchestration.multiagent.dag.audit.InMemoryDagAuditRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagReplayServiceTest {

    @Test
    void shouldReplayEventsByDagRunIdAndNodeFilter() {
        InMemoryDagAuditRepository auditRepository = new InMemoryDagAuditRepository();
        DagAuditService auditService = new DagAuditService(auditRepository);
        InMemoryEventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        DagReplayService replayService = new DagReplayService(auditService, eventLogRepository);

        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-dag:dag:1");
        runRecord.setWorkflowId("wf-dag");
        runRecord.setStatus("COMPLETED");
        runRecord.setStartedAt(Instant.now());
        auditRepository.saveRunRecord(runRecord);

        eventLogRepository.saveIfAbsent(event("wf-dag:1", "wf-dag", "tenant-a", "STEP_STARTED",
                Map.of("dagRunId", "wf-dag:dag:1", "nodeId", "n1", "attempt", 1)));
        eventLogRepository.saveIfAbsent(event("wf-dag:2", "wf-dag", "tenant-a", "STEP_COMPLETED",
                Map.of("dagRunId", "wf-dag:dag:1", "nodeId", "n1", "attempt", 1)));
        eventLogRepository.saveIfAbsent(event("wf-dag:3", "wf-dag", "tenant-a", "STEP_STARTED",
                Map.of("dagRunId", "wf-dag:dag:1", "nodeId", "n2", "attempt", 1)));

        DagReplayResponse response = replayService.replay("wf-dag",
                "tenant-a",
                "wf-dag:dag:1",
                null,
                50,
                null,
                null,
                "n1",
                null);

        assertEquals("wf-dag:dag:1", response.getDagRunId());
        assertEquals(2, response.getFrames().size());
        assertFalse(response.isHasMore());
        assertEquals("n1", response.getFrames().get(0).getNodeId());
        assertEquals("n1", response.getFrames().get(1).getNodeId());
    }

    @Test
    void shouldSupportCursorPaging() {
        InMemoryDagAuditRepository auditRepository = new InMemoryDagAuditRepository();
        DagAuditService auditService = new DagAuditService(auditRepository);
        InMemoryEventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        DagReplayService replayService = new DagReplayService(auditService, eventLogRepository);

        DagRunAuditRecord runRecord = new DagRunAuditRecord();
        runRecord.setDagRunId("wf-page:dag:1");
        runRecord.setWorkflowId("wf-page");
        runRecord.setStatus("COMPLETED");
        runRecord.setStartedAt(Instant.now());
        auditRepository.saveRunRecord(runRecord);

        eventLogRepository.saveIfAbsent(event("wf-page:1", "wf-page", "tenant-a", "STEP_STARTED",
                Map.of("dagRunId", "wf-page:dag:1", "nodeId", "n1", "attempt", 1)));
        eventLogRepository.saveIfAbsent(event("wf-page:2", "wf-page", "tenant-a", "STEP_COMPLETED",
                Map.of("dagRunId", "wf-page:dag:1", "nodeId", "n1", "attempt", 1)));

        DagReplayResponse firstPage = replayService.replay("wf-page",
                "tenant-a",
                "wf-page:dag:1",
                null,
                1,
                null,
                null,
                null,
                null);
        assertEquals(1, firstPage.getFrames().size());
        assertEquals("wf-page:1", firstPage.getNextCursor());
        assertTrue(firstPage.isHasMore());

        DagReplayResponse secondPage = replayService.replay("wf-page",
                "tenant-a",
                "wf-page:dag:1",
                firstPage.getNextCursor(),
                1,
                null,
                null,
                null,
                null);
        assertEquals(1, secondPage.getFrames().size());
        assertFalse(secondPage.isHasMore());
    }

    private EventLogRecord event(String eventId,
                                 String workflowId,
                                 String tenantId,
                                 String type,
                                 Map<String, Object> payload) {
        EventLogRecord record = new EventLogRecord();
        record.setEventId(eventId);
        record.setWorkflowId(workflowId);
        record.setTenantId(tenantId);
        record.setType(type);
        record.setTimestamp(Instant.now());
        record.setPayload(payload);
        return record;
    }
}
