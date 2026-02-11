package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagPlan;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;

class DagActorRuntimeTest {

    @Test
    void shouldExecuteReadyNodesAndThenDownstreamNodes() {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher, null);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService, eventPublisher);

        DagNode planner = new DagNode("planner",
                role("planner"),
                List.of(),
                List.of(),
                List.of("topic.plan"));
        DagNode reviewer = new DagNode("reviewer",
                role("reviewer"),
                List.of(),
                List.of(),
                List.of("topic.review"));
        DagNode writer = new DagNode("writer",
                role("writer"),
                List.of("planner", "reviewer"),
                List.of("topic.plan", "topic.review"),
                List.of("topic.out"));

        DagPlan plan = new DagPlan(List.of(planner, reviewer, writer),
                List.of("planner", "reviewer", "writer"),
                Map.of("planner", planner, "reviewer", reviewer, "writer", writer));

        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-actor", tenantContext, new AtomicLong(0), plan);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(3, result.getDagNodes().size());
        assertTrue(workspaceSyncService.hasTopic("wf-actor", "topic.plan"));
        assertTrue(workspaceSyncService.hasTopic("wf-actor", "topic.review"));
        assertTrue(workspaceSyncService.hasTopic("wf-actor", "topic.out"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());
    }

    private AgentRole role(String roleId) {
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(roleId);
        role.setDescription("desc-" + roleId);
        return role;
    }
}
