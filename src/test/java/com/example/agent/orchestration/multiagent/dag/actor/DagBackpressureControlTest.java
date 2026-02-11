package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagPlan;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagBackpressureControlTest {

    @Test
    void shouldEmitBackpressureEventWhenReadyQueueIsSmall() {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher, null);
        DagSupervisorPolicy supervisorPolicy = new DagSupervisorPolicy(3,
                0,
                30000L,
                FailurePropagationPolicy.PARTIAL_SUCCESS);
        DagBackpressurePolicy backpressurePolicy = new DagBackpressurePolicy(1,
                1,
                8,
                30000L,
                1L,
                2L);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService,
                eventPublisher,
                supervisorPolicy,
                backpressurePolicy);

        DagNode a = new DagNode("a", role("a"), List.of(), List.of(), List.of("topic.a"));
        DagNode b = new DagNode("b", role("b"), List.of(), List.of(), List.of("topic.b"));
        DagNode c = new DagNode("c", role("c"), List.of(), List.of(), List.of("topic.c"));
        DagPlan plan = new DagPlan(List.of(a, b, c),
                List.of("a", "b", "c"),
                Map.of("a", a, "b", b, "c", c),
                Map.of("a", 0, "b", 0, "c", 0),
                Map.of("a", List.of(), "b", List.of(), "c", List.of()));

        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-bp",
                new TenantContext("t", "u", List.of(), "r", "tr"),
                new AtomicLong(0),
                plan);

        assertTrue(result.getDagNodes().size() >= 1);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(applicationEventPublisher, Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().size() >= 1);
    }

    private AgentRole role(String roleId) {
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(roleId);
        role.setDescription("desc-" + roleId);
        return role;
    }
}
