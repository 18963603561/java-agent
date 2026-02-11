package com.example.agent.orchestration.multiagent.handoff;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.sse.EventStreamService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HandoffServiceTest {

    @Test
    void shouldCompleteHandoffWhenRequestIsValid() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher,
                eventStreamService);
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                eventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());

        HandoffRequest request = new HandoffRequest();
        request.setFromAgent("planner");
        request.setToAgent("writer");
        request.setContext(Map.of("topic", "analysis", "payload", "ok"));
        request.setPermissions(Map.of("mode", "inherited"));

        HandoffResult result = handoffService.handoff(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0));

        assertEquals(HandoffStatus.SUCCEEDED, result.getStatus());
        assertEquals("ok", result.getReason());
    }

    @Test
    void shouldFailWhenRequestMissingFields() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher,
                eventStreamService);
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                eventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());

        HandoffRequest request = new HandoffRequest();
        request.setFromAgent("planner");

        HandoffResult result = handoffService.handoff(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0));

        assertEquals(HandoffStatus.FAILED, result.getStatus());
        assertEquals("handoff_invalid_request", result.getReason());
    }

    @Test
    void shouldPublishMessageEventsWhenTopicPresent() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher,
                eventStreamService);
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                eventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());

        HandoffRequest request = new HandoffRequest();
        request.setFromAgent("planner");
        request.setToAgent("writer");
        request.setContext(Map.of("topic", "analysis", "payload", "ok"));

        handoffService.handoff(request,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1",
                new AtomicLong(0));

        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        Mockito.verify(applicationEventPublisher, Mockito.atLeast(1)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.MESSAGE_SENT));
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.MESSAGE_RECEIVED));
    }

    @Test
    void shouldReturnSameRecordWhenIdempotencyKeyRepeated() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher,
                eventStreamService);
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                eventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());

        HandoffRequest request = new HandoffRequest();
        request.setFromAgent("planner");
        request.setToAgent("writer");
        request.setIdempotencyKey("idem-100");
        request.setContext(Map.of("topic", "analysis", "payload", "ok"));

        TenantContext tenant = new TenantContext("t-1", "u-1", List.of(), "req", "trace");
        HandoffResult first = handoffService.handoff(request, tenant, "wf-1", new AtomicLong(0));
        HandoffResult second = handoffService.handoff(request, tenant, "wf-1", new AtomicLong(0));

        assertNotNull(first.getHandoffId());
        assertEquals(first.getHandoffId(), second.getHandoffId());
        assertEquals(first.getStatus(), second.getStatus());
    }
}
