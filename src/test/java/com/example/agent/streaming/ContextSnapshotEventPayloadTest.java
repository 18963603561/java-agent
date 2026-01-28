package com.example.agent.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContextSnapshotEventPayloadTest {

    @Test
    void payloadSerializesWithMissingFields() throws Exception {
        ContextSnapshotEventPayload payload = new ContextSnapshotEventPayload();
        payload.setTenantId("t1");
        payload.setWorkflowId("wf-1");
        payload.setStage(ContextSnapshotStage.PLAN_ASSEMBLED);

        String json = new ObjectMapper().writeValueAsString(payload);
        assertNotNull(json);
    }
}
