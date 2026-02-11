package com.example.agent.orchestration.multiagent.dag.actor.distributed;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.MultiAgentEventPublisher;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagNode;
import com.example.agent.orchestration.multiagent.dag.domain.model.DagPlan;
import com.example.agent.orchestration.multiagent.dag.actor.DagActorRuntime;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditService;
import com.example.agent.orchestration.multiagent.dag.audit.DagAuditSnapshot;
import com.example.agent.orchestration.multiagent.dag.audit.DagDependencyEventRecord;
import com.example.agent.orchestration.multiagent.handoff.WorkspaceSyncService;
import com.example.agent.security.auth.TenantContext;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagMessageSinglePathTest {

    @Test
    void shouldOnlyRecordQueuedDependencyDeliveryStatus() throws Exception {
        WorkspaceSyncService workspaceSyncService = new WorkspaceSyncService();
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        MultiAgentEventPublisher eventPublisher = new MultiAgentEventPublisher(applicationEventPublisher, null);
        DagActorRuntime runtime = new DagActorRuntime(workspaceSyncService, eventPublisher);

        DagNode planner = buildNode("planner", List.of(), List.of(), List.of("topic.plan"));
        DagNode writer = buildNode("writer", List.of("planner"), List.of("topic.plan"), List.of("topic.out"));
        DagPlan plan = new DagPlan(List.of(planner, writer),
                List.of("planner", "writer"),
                Map.of("planner", planner, "writer", writer));

        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        DagActorRuntime.DagActorRuntimeResult result = runtime.run("wf-single-path", tenantContext, new AtomicLong(0), plan);

        assertEquals("COMPLETED", result.getStatus());

        DagAuditService auditService = (DagAuditService) readField(runtime, "dagAuditService");
        Optional<DagAuditSnapshot> snapshotOptional = auditService.findSnapshot(result.getDagRunId());

        assertTrue(snapshotOptional.isPresent());
        List<DagDependencyEventRecord> dependencyEvents = snapshotOptional.get().getDependencyEvents();
        assertFalse(dependencyEvents.isEmpty());
        assertTrue(dependencyEvents.stream().allMatch(event -> "QUEUED".equals(event.getDeliveryStatus())));
    }

    private DagNode buildNode(String nodeId, List<String> dependencies, List<String> consumes, List<String> produces) {
        AgentRole role = new AgentRole();
        role.setRoleId(nodeId);
        role.setName(nodeId);
        role.setDescription("role-" + nodeId);
        return new DagNode(nodeId, role, dependencies, consumes, produces);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
