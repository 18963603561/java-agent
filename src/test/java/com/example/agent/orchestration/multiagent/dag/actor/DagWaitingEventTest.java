package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DagWaitingEventTest {

    @Test
    void shouldPublishWaitingAndBackpressureEvents() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher, null);

        eventPublisher.publishDagNodeWaiting(
                new com.example.agent.security.auth.TenantContext("t", "u", java.util.List.of(), "r", "tr"),
                "wf-wait",
                new java.util.concurrent.atomic.AtomicLong(0),
                "node-1",
                "role-1",
                "backoff",
                200L);
        eventPublisher.publishDagBackpressureApplied(
                new com.example.agent.security.auth.TenantContext("t", "u", java.util.List.of(), "r", "tr"),
                "wf-wait",
                new java.util.concurrent.atomic.AtomicLong(1),
                "node-1",
                "mailbox_full",
                10,
                8);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(applicationEventPublisher, Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().size() == 2);
    }
}

