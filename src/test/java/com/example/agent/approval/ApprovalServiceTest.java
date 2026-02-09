package com.example.agent.approval;

import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.governance.approval.ApprovalDecision;
import com.example.agent.governance.approval.ApprovalHandle;
import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.common.error.ErrorCodeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(0, service.pendingCount());
    }

    @Test
    void pendingCapacityExceededShouldRejectNewRequest() {
        ApprovalService service = buildService(3, 1, 3600, 60);
        service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-1");
        service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-2");
        service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-3");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-4"));
        assertEquals("APPROVAL_PENDING_FULL", ex.getErrorCode());
    }

    @Test
    void expiredPendingShouldBeCleanedOnNewRequest() throws InterruptedException {
        ApprovalService service = buildService(10, 50, 1, 1);
        service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-1");
        Thread.sleep(1200);

        service.requestApproval("tenant-a", "workflow-a", null, "tool-a", "digest-2");

        assertEquals(1, service.pendingCount());
    }

    private ApprovalService buildService() {
        return buildService(2000, 300, 1800, 30);
    }

    private ApprovalService buildService(int maxSize, int timeoutSeconds, int ttlSeconds, int cleanupIntervalSeconds) {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(true);
        properties.setPendingMaxSize(maxSize);
        properties.setTimeoutSeconds(timeoutSeconds);
        properties.setPendingTtlSeconds(ttlSeconds);
        properties.setCleanupIntervalSeconds(cleanupIntervalSeconds);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties, metricsPublisher);
    }
}
