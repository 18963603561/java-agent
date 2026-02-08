package com.example.agent.planning.strategy;

import com.example.agent.planning.context.PlanningContext;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 策略上下文不变式测试。
 */
class PlanningStrategyContextInvariantTest {

    @Test
    void constructorShouldDefensiveCopyExternalLists() {
        List<StepSpec> steps = new ArrayList<>();
        List<Map<String, Object>> planSteps = new ArrayList<>();
        List<Map<String, String>> dependencies = new ArrayList<>();

        PlanningStrategyContext context = new PlanningStrategyContext("plan-1",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                "simple",
                "sequential",
                "",
                "",
                steps,
                planSteps,
                dependencies,
                null);

        steps.add(new StepSpec());
        planSteps.add(Map.of("id", "step-1"));
        dependencies.add(Map.of("from", "s1", "to", "s2"));

        assertEquals(0, context.getStepCount());
        assertEquals(0, context.getPlanSteps().size());
        assertEquals(0, context.getDependencies().size());
    }

    @Test
    void getterShouldExposeUnmodifiableViews() {
        PlanningStrategyContext context = new PlanningStrategyContext("plan-2",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                "simple",
                "sequential",
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        assertThrows(UnsupportedOperationException.class, () -> context.getSteps().add(new StepSpec()));
        assertThrows(UnsupportedOperationException.class,
                () -> context.getPlanSteps().add(Map.of("id", "step-1")));
        assertThrows(UnsupportedOperationException.class,
                () -> context.getDependencies().add(Map.of("from", "s1", "to", "s2")));
    }

    @Test
    void appendMethodsShouldMutateInternalStateOnly() {
        PlanningStrategyContext context = new PlanningStrategyContext("plan-3",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                "simple",
                "sequential",
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        StepSpec step = new StepSpec();
        step.setStepType("TOOL");
        context.appendStep(step, Map.of("id", "step-1"));
        context.appendDependency(Map.of("from", "step-0", "to", "step-1"));

        assertEquals(1, context.getStepCount());
        assertEquals("TOOL", context.getSteps().get(0).getStepType());
        assertEquals(1, context.getPlanSteps().size());
        assertEquals(1, context.getDependencies().size());
    }
}

