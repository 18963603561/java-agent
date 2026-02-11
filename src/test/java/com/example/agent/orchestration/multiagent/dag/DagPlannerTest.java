package com.example.agent.orchestration.multiagent.dag;

import com.example.agent.orchestration.multiagent.AgentRole;
import com.example.agent.orchestration.multiagent.support.StepArgumentReader;
import com.example.agent.runtime.model.StepSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DagPlannerTest {

    @Test
    void shouldBuildTopologicalOrderForLinearDependencies() {
        DagPlanner planner = new DagPlanner(new StepArgumentReader());
        AgentRole roleA = role("a", "A");
        AgentRole roleB = role("b", "B");
        AgentRole roleC = role("c", "C");

        StepSpec step = new StepSpec("MULTI_AGENT", Map.of(
                "consumes", List.of("topic.in"),
                "produces", List.of("topic.out")
        ));
        step.setDependsOn(List.of("a", "b"));

        DagPlan plan = planner.build(List.of(roleA, roleB, roleC), step);

        assertEquals(3, plan.getTopologicalOrder().size());
        assertEquals("a", plan.getTopologicalOrder().get(0));
        assertEquals("b", plan.getTopologicalOrder().get(1));
        assertEquals("c", plan.getTopologicalOrder().get(2));
        assertEquals(List.of("topic.in"), plan.getNodeIndex().get("b").getConsumes());
        assertEquals(List.of("topic.out"), plan.getNodeIndex().get("c").getProduces());
        assertEquals(0, plan.getInDegree().get("a"));
        assertEquals(2, plan.getInDegree().get("c"));
        assertEquals(List.of("b", "c"), plan.getDownstream().get("a"));
        assertEquals(List.of("c"), plan.getDownstream().get("b"));
    }

    @Test
    void shouldThrowWhenCycleDetected() {
        DagPlanner planner = new DagPlanner(new StepArgumentReader());
        AgentRole roleA = role("a", "A");
        AgentRole roleB = role("b", "B");

        StepSpec step = new StepSpec("MULTI_AGENT", Map.of(
                "dagDependencies", Map.of(
                        "a", List.of("b"),
                        "b", List.of("a")
                )
        ));

        assertThrows(DagCycleDetectedException.class,
                () -> planner.build(List.of(roleA, roleB), step));
    }

    @Test
    void shouldRejectWhenDependencyNodeMissing() {
        DagPlanner planner = new DagPlanner(new StepArgumentReader());
        AgentRole roleA = role("a", "A");

        StepSpec step = new StepSpec("MULTI_AGENT", Map.of(
                "dagDependencies", Map.of(
                        "a", List.of("missing")
                )
        ));

        assertThrows(IllegalArgumentException.class,
                () -> planner.build(List.of(roleA), step));
    }

    @Test
    void shouldRejectSelfDependency() {
        DagPlanner planner = new DagPlanner(new StepArgumentReader());
        AgentRole roleA = role("a", "A");

        StepSpec step = new StepSpec("MULTI_AGENT", Map.of(
                "dagDependencies", Map.of(
                        "a", List.of("a")
                )
        ));

        assertThrows(DagCycleDetectedException.class,
                () -> planner.build(List.of(roleA), step));
    }

    private AgentRole role(String roleId, String name) {
        AgentRole role = new AgentRole();
        role.setRoleId(roleId);
        role.setName(name);
        role.setDescription("desc-" + roleId);
        return role;
    }
}
