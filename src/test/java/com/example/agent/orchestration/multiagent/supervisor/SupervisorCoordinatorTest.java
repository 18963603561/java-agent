package com.example.agent.orchestration.multiagent.supervisor;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.model.SupervisorExecutionResult;
import com.example.agent.orchestration.multiagent.handoff.HandoffService;
import com.example.agent.orchestration.multiagent.handoff.HandoffStateMachine;
import com.example.agent.orchestration.multiagent.handoff.InMemoryHandoffRepository;
import com.example.agent.orchestration.multiagent.handoff.TopicDependencyCoordinator;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
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

class SupervisorCoordinatorTest {

    @Test
    void shouldReturnPartialSuccessWhenHandoffFailsAndPolicyAllows() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher,
                eventStreamService);

        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        TopicDependencyCoordinator dependencyCoordinator = new TopicDependencyCoordinator(workspaceSyncService,
                200,
                10);
        HandoffService handoffService = new HandoffService(
                workspaceSyncService,
                eventPublisher,
                new InMemoryHandoffRepository(),
                new HandoffStateMachine());
        SupervisorPolicy supervisorPolicy = new SupervisorPolicy(3, "PARTIAL_SUCCESS");
        SupervisorCoordinator coordinator = new SupervisorCoordinator(supervisorPolicy,
                handoffService,
                dependencyCoordinator,
                eventPublisher);

        AgentRole roleA = new AgentRole();
        roleA.setRoleId("a");
        roleA.setName("A");
        roleA.setDescription("desc-a");

        AgentRole roleB = new AgentRole();
        roleB.setRoleId("b");
        roleB.setName("B");
        roleB.setDescription("desc-b");

        SupervisorExecutionResult result = coordinator.coordinate(
                "wf-1",
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                new AtomicLong(0),
                List.of(roleA, roleB),
                List.of("missing-topic"),
                List.of()
        );

        assertEquals("PARTIAL_SUCCESS", result.getStatus());
        assertTrue(result.getMissingTopics().contains("missing-topic"));

        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        Mockito.verify(applicationEventPublisher, Mockito.atLeast(1)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertTrue(events.stream().anyMatch(event -> event.getType() == EventType.TEAM_STATUS));
    }
}
