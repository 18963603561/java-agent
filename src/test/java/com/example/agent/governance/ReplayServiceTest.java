package com.example.agent.governance;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskStatusResponse;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.history.eventlog.EventLogRecord;
import com.example.agent.history.eventlog.EventLogRepository;
import com.example.agent.history.eventlog.InMemoryEventLogRepository;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.orchestration.task.TaskQueryService;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.governance.replay.ReplayRequest;
import com.example.agent.governance.replay.ReplayResponse;
import com.example.agent.governance.replay.ReplayService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReplayServiceTest {

    @Test
    void replayPublishesEventsInOrder() {
        TaskQueryService taskQueryService = Mockito.mock(TaskQueryService.class);
        EventLogRepository eventLogRepository = new InMemoryEventLogRepository();
        StepRuntimeService stepRuntimeService = Mockito.mock(StepRuntimeService.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        CollectingEventPublisher eventPublisher = new CollectingEventPublisher();

        TenantContext tenantContext = new TenantContext("tenant-a", "user-1", List.of(), "req", "trace");
        TaskStatusResponse status = new TaskStatusResponse("task-1", "wf-1", "COMPLETED", Instant.now(), null);
        when(taskQueryService.getTask(anyString(), any())).thenReturn(status);

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

        ReplayService replayService = new ReplayService(taskQueryService, eventLogRepository, stepRuntimeService,
                eventPublisher, eventStreamService, metricsPublisher);

        ReplayRequest request = new ReplayRequest();
        request.setTaskId("task-1");
        request.setMode("full");

        ReplayResponse response = replayService.replay(request, tenantContext);
        assertNotNull(response.getReplayId());

        List<StreamEvent> events = eventPublisher.getStreamEvents();
        assertEquals(EventType.REPLAY_STARTED, events.get(0).getType());
        assertEquals(EventType.WORKFLOW_STARTED, events.get(1).getType());
        assertEquals(EventType.STEP_COMPLETED, events.get(2).getType());
        assertEquals(EventType.REPLAY_COMPLETED, events.get(events.size() - 1).getType());
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
