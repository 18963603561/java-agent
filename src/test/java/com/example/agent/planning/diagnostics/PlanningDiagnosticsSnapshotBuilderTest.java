package com.example.agent.planning.diagnostics;

import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.strategy.PlanningStrategyContext;
import com.example.agent.planning.strategy.PlanningStrategyResult;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规划诊断快照构建器测试。
 */
class PlanningDiagnosticsSnapshotBuilderTest {

    @Test
    void buildContextSnapshotShouldIncludeSummary() {
        PlanningDiagnosticsSnapshotBuilder builder = new PlanningDiagnosticsSnapshotBuilder();
        Map<String, Object> values = new HashMap<>();
        values.put(PlanningContextKeys.TOOL, "demo_tool");
        PlanningContext context = new PlanningContext(values);

        Map<String, Object> snapshot = builder.buildContextSnapshot(context);

        assertTrue(Boolean.TRUE.equals(snapshot.get("available")));
        assertEquals(1, snapshot.get("keys"));
        assertTrue(snapshot.containsKey("summary"));
    }

    @Test
    void buildStrategySnapshotShouldExposeCoreFields() {
        PlanningDiagnosticsSnapshotBuilder builder = new PlanningDiagnosticsSnapshotBuilder();
        PlanningStrategyContext context = new PlanningStrategyContext("plan-1",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.3,
                "simple",
                "sequential",
                "react",
                "react",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);
        context.appendStep(new StepSpec(), Map.of("id", "step-1"));
        PlanningStrategyResult result = PlanningStrategyResult.terminal(List.of(new StepSpec()),
                List.of(Map.of("id", "step-1")),
                List.of(),
                "ReAct",
                "step-1");

        Map<String, Object> snapshot = builder.buildStrategySnapshot(context, result);

        assertEquals("plan-1", snapshot.get("planId"));
        assertEquals(1, snapshot.get("stepCount"));
        assertTrue(Boolean.TRUE.equals(snapshot.get("terminal")));
        assertEquals("ReAct", snapshot.get("scene"));
    }
}

