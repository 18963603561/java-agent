package com.example.agent.orchestration.multiagent.handoff;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSyncServiceTest {

    @Test
    void shouldAppendAndReadWorkspaceTopic() {
        WorkspaceSyncService service = new WorkspaceSyncService();
        service.append("wf-1", "analysis", Map.of("value", "ok"));

        assertTrue(service.hasTopic("wf-1", "analysis"));
        assertFalse(service.list("wf-1", "analysis").isEmpty());
    }

    @Test
    void shouldReturnFalseForMissingTopic() {
        WorkspaceSyncService service = new WorkspaceSyncService();
        assertFalse(service.hasTopic("wf-1", "missing"));
    }

    @Test
    void shouldReturnWorkflowSnapshotAndLatestEntry() {
        WorkspaceSyncService service = new WorkspaceSyncService();
        service.append("wf-2", "analysis", Map.of("value", "v1"));
        service.append("wf-2", "analysis", Map.of("value", "v2"));
        service.append("wf-2", "draft", Map.of("value", "d1"));

        Map<String, java.util.List<Map<String, Object>>> snapshot = service.snapshot("wf-2");
        assertEquals(2, snapshot.size());
        assertEquals(2, snapshot.get("analysis").size());

        Map<String, Object> latest = service.latest("wf-2", "analysis");
        assertEquals("v2", latest.get("value"));
    }
}
