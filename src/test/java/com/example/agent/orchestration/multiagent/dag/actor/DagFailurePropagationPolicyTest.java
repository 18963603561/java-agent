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
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DagFailurePropagationPolicyTest {

    @Test
    void shouldFailFastWhenFailurePolicyIsFailFast() {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(
                Mockito.mock(ApplicationEventPublisher.class),
                null);
        DagSupervisorPolicy supervisorPolicy = new DagSupervisorPolicy(2,
                0,
                30000L,
                FailurePropagationPolicy.FAIL_FAST);
        DagBackpressurePolicy backpressurePolicy = new DagBackpressurePolicy(2,
                16,
                8,
                30000L,
                10L,
                20L);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService,
                eventPublisher,
                supervisorPolicy,
                backpressurePolicy);

        DagNode failed = new DagNode("failed",
                role("failed"),
                List.of(),
                List.of(),
                List.of("FAIL_TOPIC"));
        DagNode downstream = new DagNode("downstream",
                role("downstream"),
                List.of("failed"),
                List.of("FAIL_TOPIC"),
                List.of("topic.downstream"));
        DagPlan plan = new DagPlan(List.of(failed, downstream),
                List.of("failed", "downstream"),
                Map.of("failed", failed, "downstream", downstream),
                Map.of("failed", 0, "downstream", 1),
                Map.of("failed", List.of("downstream"), "downstream", List.of()));

        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-fail-fast",
                new TenantContext("t", "u", List.of(), "r", "tr"),
                new AtomicLong(0),
                plan);

        assertEquals("FAILED", result.getStatus());
    }

    @Test
    void shouldReturnPartialSuccessWhenPolicyAllowsPartialAndSomeNodesSucceeded() {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(
                Mockito.mock(ApplicationEventPublisher.class),
                null);
        DagSupervisorPolicy supervisorPolicy = new DagSupervisorPolicy(1,
                0,
                30000L,
                FailurePropagationPolicy.PARTIAL_SUCCESS);
        DagBackpressurePolicy backpressurePolicy = new DagBackpressurePolicy(2,
                16,
                8,
                30000L,
                10L,
                20L);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService,
                eventPublisher,
                supervisorPolicy,
                backpressurePolicy);

        DagNode ok = new DagNode("ok",
                role("ok"),
                List.of(),
                List.of(),
                List.of("topic.ok"));
        DagNode failed = new DagNode("failed",
                role("failed"),
                List.of(),
                List.of(),
                List.of("FAIL_TOPIC"));
        DagPlan plan = new DagPlan(List.of(ok, failed),
                List.of("ok", "failed"),
                Map.of("ok", ok, "failed", failed),
                Map.of("ok", 0, "failed", 0),
                Map.of("ok", List.of(), "failed", List.of()));

        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-partial",
                new TenantContext("t", "u", List.of(), "r", "tr"),
                new AtomicLong(0),
                plan);

        assertEquals("PARTIAL_SUCCESS", result.getStatus());
    }

    private AgentRole role(String roleId) {
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(roleId);
        role.setDescription("desc-" + roleId);
        return role;
    }
}
