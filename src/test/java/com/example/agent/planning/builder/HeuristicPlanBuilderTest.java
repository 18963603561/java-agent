package com.example.agent.planning.builder;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.parser.PlanParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicPlanBuilderTest {

    private final HeuristicPlanBuilder builder = new HeuristicPlanBuilder(new PlanParser(new ObjectMapper()));

    @Test
    void buildGeneratesChainOfThoughtWhenModeCot() {
        Map<String, Object> values = new HashMap<>();
        values.put(PlanningContextKeys.MODE, "cot");

        var plan = builder.build("plan-cot", "请逐步分析这个问题", new PlanningContext(values), "t-1");

        assertNotNull(plan);
        assertEquals(1, plan.getSteps().size());
        assertEquals("CHAIN_OF_THOUGHT", plan.getSteps().get(0).getStepType());
        assertTrue(values.containsKey(PlanningContextKeys.PLAN_STEPS));
    }

    @Test
    void buildGeneratesReactWhenReactEnabled() {
        Map<String, Object> values = new HashMap<>();
        values.put(PlanningContextKeys.REACT_ENABLED, true);

        var plan = builder.build("plan-react", "react query", new PlanningContext(values), "t-1");

        assertNotNull(plan);
        assertTrue(plan.getSteps().stream().anyMatch(step -> "REACT".equals(step.getStepType())));
    }

    @Test
    void buildGeneratesLlmWhenToolsDisabled() {
        Map<String, Object> values = new HashMap<>();
        values.put(PlanningContextKeys.TOOL_CHOICE, ModelToolChoice.none());

        var plan = builder.build("plan-llm", "llm only", new PlanningContext(values), "t-1");

        assertNotNull(plan);
        assertEquals(1, plan.getSteps().size());
        assertEquals("LLM", plan.getSteps().get(0).getStepType());
    }

    @Test
    void buildGeneratesToolStepByDefault() {
        Map<String, Object> values = new HashMap<>();
        values.put(PlanningContextKeys.TOOL, "demo_tool");
        values.put(PlanningContextKeys.FALLBACK_TOOL, "backup_tool");

        var plan = builder.build("plan-tool", "ping", new PlanningContext(values), "t-1");

        assertNotNull(plan);
        assertEquals(1, plan.getSteps().size());
        assertEquals("TOOL", plan.getSteps().get(0).getStepType());
        assertEquals("demo_tool", plan.getSteps().get(0).getArguments().get(PlanningContextKeys.TOOL));
        assertEquals("backup_tool", plan.getSteps().get(0).getArguments().get(PlanningContextKeys.FALLBACK_TOOL));
    }

    @Test
    void estimateComplexityReturnsStableRange() {
        double empty = builder.estimateComplexity("");
        double simple = builder.estimateComplexity("ping");
        double complex = builder.estimateComplexity("请综合多维信息，分步论证并给出方案、风险、回滚与验证步骤。".repeat(4));

        assertTrue(empty >= 0.0 && empty <= 1.0);
        assertTrue(simple >= 0.0 && simple <= 1.0);
        assertTrue(complex >= 0.0 && complex <= 1.0);
        assertTrue(complex >= simple);
    }
}

