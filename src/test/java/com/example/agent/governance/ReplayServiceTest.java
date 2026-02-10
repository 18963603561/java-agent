package com.example.agent.governance;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.history.eventlog.InMemoryEventLogRepository;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.orchestration.task.TaskRecord;
import com.example.agent.orchestration.task.TaskRepository;
import com.example.agent.orchestration.task.TaskStatus;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.streaming.sse.EventStreamService;
import com.example.agent.governance.replay.domain.ReplayCommand;
import com.example.agent.governance.replay.domain.ReplayEventFactory;
import com.example.agent.governance.replay.domain.ReplayItemAssembler;
import com.example.agent.governance.replay.domain.ReplayResult;
import com.example.agent.governance.replay.domain.ReplaySessionStore;
import com.example.agent.governance.replay.domain.ReplayTaskResolver;
import com.example.agent.governance.common.telemetry.GovernanceTelemetry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.governance.replay.ReplayProperties;
import com.example.agent.governance.replay.ReplayService;
import org.springframework.http.HttpStatus;
import com.example.agent.common.error.ErrorCodeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReplayServiceTest {

    @Test
    void replayPublishesEventsInOrder() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        TaskRecord status = buildTaskRecord("tenant-a", "task-1", "wf-1", "COMPLETED");
        when(taskRepository.findById("tenant-a", "task-1")).thenReturn(status);

        EventLogRecord record = new EventLogRecord();
        record.setEventId("wf-1:1");
        record.setWorkflowId("wf-1");
        record.setType("WORKFLOW_STARTED");
        record.setTimestamp(Instant.now().minusSeconds(10));
        record.setTenantId("tenant-a");
        record.setPayload(java.util.Map.of("message", "started"));
        eventLogRepository.saveIfAbsent(record);

        StepRecord step = new StepRecord();
        step.setStepId("step-1");
        step.setWorkflowId("wf-1");
        step.setStepSeq(2);
        step.setStatus(StepState.COMPLETED);
        step.setCompletedAt(Instant.now().minusSeconds(5));
        when(stepRuntimeService.getSteps("wf-1", tenantContext)).thenReturn(List.of(step));

        when(eventStreamService.sequenceCounter(anyString(), anyString())).thenReturn(new AtomicLong(0));

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-1", null, null, "full");

        ReplayResult response = replayService.replay(command, tenantContext);
        assertNotNull(response.getReplayId());

        List<StreamEvent> events = eventPublisher.getStreamEvents();
        assertEquals(EventType.REPLAY_STARTED, events.get(0).getType());
        assertEquals(EventType.WORKFLOW_STARTED, events.get(1).getType());
        assertEquals(EventType.STEP_COMPLETED, events.get(2).getType());
        assertEquals(EventType.REPLAY_COMPLETED, events.get(events.size() - 1).getType());
    }

    @Test
    void replayTaskNotFoundShouldReturnReplayNotFound() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        when(taskRepository.findById("tenant-a", "task-404")).thenReturn(null);

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-404", null, null, "full");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> replayService.replay(command, tenantContext));
        assertEquals("REPLAY_NOT_FOUND", ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void replayTaskQueryFailureShouldReturnServiceUnavailable() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        when(taskRepository.findById("tenant-a", "task-500"))
                .thenThrow(new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "TASK_QUERY_FAILED", "task query failed"));

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-500", null, null, "full");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> replayService.replay(command, tenantContext));
        assertEquals("REPLAY_TASK_QUERY_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void replayUnexpectedExceptionShouldReturnInternalServerError() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        when(taskRepository.findById("tenant-a", "task-ex"))
                .thenThrow(new IllegalStateException("boom"));

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-ex", null, null, "full");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> replayService.replay(command, tenantContext));
        assertEquals("REPLAY_TASK_QUERY_EXCEPTION", ex.getErrorCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    @Test
    void replayMissingTaskIdShouldReturnBadRequest() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand(" ", null, null, "full");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> replayService.replay(command, tenantContext));
        assertEquals("REPLAY_TASK_MISSING", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void replayTaskNotFoundShouldEmitSingleNotFoundMetric() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        when(taskRepository.findById("tenant-a", "task-404")).thenReturn(null);

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-404", null, null, "full");

        assertThrows(ErrorCodeException.class, () -> replayService.replay(command, tenantContext));

        Mockito.verify(metricsPublisher, Mockito.times(1)).incrementWithTags(
                Mockito.eq("governance.replay.resolve_task.total"),
                Mockito.eq("domain"), Mockito.eq("replay"),
                Mockito.eq("action"), Mockito.eq("resolve_task"),
                Mockito.eq("result"), Mockito.eq("not_found"));
    }

    @Test
    void replayMissingTenantShouldReturnBadRequest() {
        TaskRepository taskRepository = Mockito.mock(TaskRepository.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        ReplayService replayService = buildReplayService(taskRepository,
                eventLogRepository,
                stepRuntimeService,
                eventPublisher,
                eventStreamService,
                metricsPublisher,
                buildReplayProperties());

        ReplayCommand command = new ReplayCommand("task-1", null, null, "full");

        ErrorCodeException ex = assertThrows(ErrorCodeException.class,
                () -> replayService.replay(command, null));
        assertEquals("REPLAY_TENANT_MISSING", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("回放请求缺少租户信息"));
    }

    private ReplayProperties buildReplayProperties() {
        ReplayProperties properties = new ReplayProperties();
        properties.setSessionTtlSeconds(1);
        properties.setCleanupIntervalSeconds(1);
        properties.setSessionMaxSize(2000);
        return properties;
    }

    private ReplayService buildReplayService(TaskRepository taskRepository,
                                             EventLogRepository eventLogRepository,
                                             StepRuntimeService stepRuntimeService,
                                             ApplicationEventPublisher eventPublisher,
                                             EventStreamService eventStreamService,
                                             MetricsPublisher metricsPublisher,
                                             ReplayProperties replayProperties) {
        GovernanceTelemetry governanceTelemetry = new GovernanceTelemetry(metricsPublisher);
        ReplayTaskResolver taskResolver = new ReplayTaskResolver(taskRepository, governanceTelemetry);
        ReplayItemAssembler itemAssembler = new ReplayItemAssembler(eventLogRepository, stepRuntimeService);
        ReplayEventFactory replayEventFactory = new ReplayEventFactory();
        ReplaySessionStore sessionStore = new ReplaySessionStore(metricsPublisher);
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

    private TaskRecord buildTaskRecord(String tenantId, String taskId, String workflowId, String status) {
        TaskRecord record = new TaskRecord();
        record.setTenantId(tenantId);
        record.setTaskId(taskId);
        record.setWorkflowId(workflowId);
        record.setStatus(TaskStatus.require(status));
        record.setUpdatedAt(Instant.now());
        return record;
    }

    private static class CollectingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        @Override
        public void publishEvent(org.springframework.context.ApplicationEvent event) {
            events.add(event);
        }

        private List<StreamEvent> getStreamEvents() {
            List<StreamEvent> streamEvents = new ArrayList<>();
            for (Object event : events) {
                if (event instanceof StreamEvent streamEvent) {
                    streamEvents.add(streamEvent);
                }
            }
            return streamEvents;
        }
    }
}
