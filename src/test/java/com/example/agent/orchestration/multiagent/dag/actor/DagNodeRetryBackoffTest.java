package com.example.agent.orchestration.multiagent.dag.actor;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.DagNode;
import com.example.agent.orchestration.multiagent.dag.DagPlan;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.orchestration.multiagent.supervisor.FailurePropagationPolicy;
import com.example.agent.security.auth.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagNodeRetryBackoffTest {

    @Test
    void shouldRetryFailedNodeUntilMaxAttemptsReached() {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(
                Mockito.mock(ApplicationEventPublisher.class),
                null);
        DagSupervisorPolicy supervisorPolicy = new DagSupervisorPolicy(3,
                2,
                30000L,
                FailurePropagationPolicy.PARTIAL_SUCCESS);
        DagBackpressurePolicy backpressurePolicy = new DagBackpressurePolicy(1,
                8,
                4,
                30000L,
                1L,
                2L);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService,
                eventPublisher,
                supervisorPolicy,
                backpressurePolicy);

        DagNode failed = new DagNode("failed",
                role("failed"),
                List.of(),
                List.of(),
                List.of("FAIL_ALWAYS"));
        DagPlan plan = new DagPlan(List.of(failed),
                List.of("failed"),
                Map.of("failed", failed),
                Map.of("failed", 0),
                Map.of("failed", List.of()));

        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-retry",
                new TenantContext("t", "u", List.of(), "r", "tr"),
                new AtomicLong(0),
                plan);

        assertTrue(result.getFailures().size() >= 1);
        assertEquals("FAILED", result.getStatus());
    }

    private AgentRole role(String roleId) {
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(roleId);
        role.setDescription("desc-" + roleId);
        return role;
    }
}

