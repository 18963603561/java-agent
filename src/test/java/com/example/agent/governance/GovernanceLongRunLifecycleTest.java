package com.example.agent.governance;

import com.example.agent.governance.approval.ApprovalDecision;
import com.example.agent.governance.approval.ApprovalHandle;
import com.example.agent.governance.approval.ApprovalProperties;
import com.example.agent.governance.approval.ApprovalService;
import com.example.agent.governance.replay.ReplayProperties;
import com.example.agent.governance.replay.ReplayService;
import com.example.agent.governance.replay.domain.ReplayCommand;
import com.example.agent.governance.replay.domain.ReplayEventFactory;
import com.example.agent.governance.replay.domain.ReplayItemAssembler;
import com.example.agent.governance.replay.domain.ReplaySessionStore;
import com.example.agent.governance.replay.domain.ReplayTaskResolver;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.history.eventlog.InMemoryEventLogRepository;
import com.example.agent.orchestration.task.TaskRecord;
import com.example.agent.orchestration.task.TaskRepository;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 治理审批与回放长稳生命周期测试。
 */
class GovernanceLongRunLifecycleTest {

    @Test
    void approvalShouldRemainStableAcrossLoops() {
        ApprovalService approvalService = buildApprovalService();
        for (int index = 0; index < 20; index++) {
            ApprovalHandle handle = approvalService.requestApproval(
                    "tenant-a",
                    "workflow-" + index,
                    "snapshot-" + index,
                    "tool-a",
                    "digest-" + index);
            approvalService.decide("tenant-a", "workflow-" + index, handle.getRequestId(), true, "ok");
            ApprovalDecision decision = approvalService.awaitDecision(handle, 2);
            assertNotNull(decision);
            assertFalse(decision.isTimeout());
        }
        assertEquals(0, approvalService.pendingCount());
    }

    @Test
    void replayShouldRemainStableAcrossLoops() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());
        when(eventStreamService.sequenceCounter(anyString(), anyString())).thenReturn(new AtomicLong(0));

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        for (int index = 0; index < 20; index++) {
            TaskRecord taskRecord = new TaskRecord();
            taskRecord.setTenantId("tenant-a");
            taskRecord.setTaskId("task-" + index);
            taskRecord.setWorkflowId("wf-" + index);
            taskRecord.setStatus("COMPLETED");
            taskRecord.setUpdatedAt(Instant.now());
            when(taskRepository.findById("tenant-a", "task-" + index)).thenReturn(taskRecord);

            replayService.replay(new ReplayCommand("task-" + index, null, null, "full"), tenantContext);
        }

        assertEquals(20, replayService.sessionCount());
    }

    private ApprovalService buildApprovalService() {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setEnabled(true);
        properties.setPendingMaxSize(100);
        properties.setPendingTtlSeconds(1800);
        properties.setCleanupIntervalSeconds(1);
        properties.setTimeoutSeconds(30);
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        return new ApprovalService(properties, metricsPublisher);
    }

    private ReplayProperties buildReplayProperties() {
        ReplayProperties properties = new ReplayProperties();
        properties.setSessionTtlSeconds(1800);
        properties.setCleanupIntervalSeconds(1);
        properties.setSessionMaxSize(100);
        return properties;
    }

    private ReplayService buildReplayService(TaskRepository taskRepository,
                                             EventLogRepository eventLogRepository,
                                             StepRuntimeService stepRuntimeService,
                                             ApplicationEventPublisher eventPublisher,
                                             EventStreamService eventStreamService,
                                             MetricsPublisher metricsPublisher,
                                             ReplayProperties replayProperties) {
        ReplayTaskResolver taskResolver = new ReplayTaskResolver(taskRepository);
        ReplayItemAssembler itemAssembler = new ReplayItemAssembler(eventLogRepository, stepRuntimeService);
        ReplayEventFactory replayEventFactory = new ReplayEventFactory();
        ReplaySessionStore sessionStore = new ReplaySessionStore(metricsPublisher);
        GovernanceTelemetry governanceTelemetry = new GovernanceTelemetry(metricsPublisher);
        return new ReplayService(eventPublisher,
                eventStreamService,
                metricsPublisher,
                replayProperties,
                taskResolver,
                itemAssembler,
                replayEventFactory,
                sessionStore,
                governanceTelemetry);
    }
}
