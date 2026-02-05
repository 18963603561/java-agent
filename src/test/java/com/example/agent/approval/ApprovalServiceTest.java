package com.example.agent.approval;

import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.governance.approval.ApprovalDecision;
import com.example.agent.governance.approval.ApprovalHandle;
import com.example.agent.governance.approval.ApprovalProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalServiceTest {

    @Test
    void requestThenApproveShouldReturnApprovedDecision() {
        ApprovalService service = buildService();
        ApprovalHandle handle = service.requestApproval("tenant-a", "workflow-a", "snapshot-a", "tool-a", "digest");
        service.decide("tenant-a", "workflow-a", handle.getRequestId(), true, "ok");

        ApprovalDecision decision = service.awaitDecision(handle, 2);

        assertNotNull(decision);
        assertTrue(decision.isApproved());
        assertFalse(decision.isTimeout());
        assertEquals(handle.getRequestId(), decision.getRequestId());
    }

    @Test
    void requestThenRejectShouldReturnRejectedDecision() {
        ApprovalService service = buildService();
        ApprovalHandle handle = service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest");
        service.decide("tenant-a", "workflow-a", handle.getRequestId(), false, "reject");

        ApprovalDecision decision = service.awaitDecision(handle, 2);

        assertNotNull(decision);
        assertFalse(decision.isApproved());
        assertFalse(decision.isTimeout());
        assertEquals(handle.getRequestId(), decision.getRequestId());
    }

    @Test
    void requestTimeoutShouldCleanPending() {
        ApprovalService service = buildService();
        ApprovalHandle handle = service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest");

        ApprovalDecision decision = service.awaitDecision(handle, 1);

        assertNotNull(decision);
        assertFalse(decision.isApproved());
        assertTrue(decision.isTimeout());

        @SuppressWarnings("unchecked")
        Map<String, Object> pending = (Map<String, Object>) ReflectionTestUtils.getField(service, "pendingApprovals");
        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }

    private ApprovalService buildService() {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(true);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties, metricsPublisher);
    }
}
