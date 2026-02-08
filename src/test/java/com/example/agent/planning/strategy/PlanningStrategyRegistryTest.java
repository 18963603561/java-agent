package com.example.agent.planning.strategy;

import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.context.PlanningContext;
import com.example.agent.planning.parser.PlanParser;
import com.example.agent.planning.strategy.handlers.ChainOfThoughtStrategyHandler;
import com.example.agent.planning.strategy.handlers.DebateStrategyHandler;
import com.example.agent.planning.strategy.handlers.DirectLlmStrategyHandler;
import com.example.agent.planning.strategy.handlers.MultiAgentStrategyHandler;
import com.example.agent.planning.strategy.handlers.ReactStrategyHandler;
import com.example.agent.planning.strategy.handlers.ResearchStrategyHandler;
import com.example.agent.planning.strategy.handlers.ThoughtTreeStrategyHandler;
import com.example.agent.planning.strategy.handlers.ToolFallbackStrategyHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningStrategyRegistryTest {

    private final PlanParser planParser = new PlanParser(new ObjectMapper());

    private PlanningStrategyRegistry newRegistry() {
        return new PlanningStrategyRegistry(List.of(
                new ChainOfThoughtStrategyHandler(planParser),
                new ThoughtTreeStrategyHandler(planParser),
                new MultiAgentStrategyHandler(planParser),
                new DebateStrategyHandler(planParser),
                new ResearchStrategyHandler(planParser),
                new ReactStrategyHandler(planParser),
                new DirectLlmStrategyHandler(planParser),
                new ToolFallbackStrategyHandler(planParser)
        ));
    }

    @Test
    void executeShouldTerminalOnCotStrategy() {
        PlanningStrategyRegistry registry = newRegistry();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(PlanningContextKeys.MODE, PlanningContextKeys.MODE_COT);
        PlanningStrategyContext context = new PlanningStrategyContext("plan-1",
                "t-1",
                "query",
                new PlanningContext(contextMap),
                0.2,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                PlanningContextKeys.MODE_COT,
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        PlanningStrategyResult result = registry.execute(context);

        assertTrue(result.isTerminal());
        assertEquals("链式推理", result.getScene());
        assertEquals(1, result.getSteps().size());
        assertEquals("CHAIN_OF_THOUGHT", result.getSteps().get(0).getStepType());
    }

    @Test
    void executeShouldComposeAndTerminalOnToolFallback() {
        PlanningStrategyRegistry registry = newRegistry();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(PlanningContextKeys.STRATEGY, PlanningContextKeys.STRATEGY_MULTI_AGENT);
        contextMap.put(PlanningContextKeys.TOOL, "demo_tool");
        PlanningStrategyContext context = new PlanningStrategyContext("plan-2",
                "t-1",
                "query",
                new PlanningContext(contextMap),
                0.3,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                PlanningContextKeys.STRATEGY_MULTI_AGENT,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        PlanningStrategyResult result = registry.execute(context);

        assertTrue(result.isTerminal());
        assertEquals("规则回退", result.getScene());
        assertEquals(2, result.getSteps().size());
        assertEquals("MULTI_AGENT", result.getSteps().get(0).getStepType());
        assertEquals("TOOL", result.getSteps().get(1).getStepType());
    }

    @Test
    void executeShouldTerminalOnReact() {
        PlanningStrategyRegistry registry = newRegistry();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(PlanningContextKeys.REACT_ENABLED, true);
        contextMap.put(PlanningContextKeys.TOOL, "demo_tool");
        PlanningStrategyContext context = new PlanningStrategyContext("plan-3",
                "t-1",
                "query",
                new PlanningContext(contextMap),
                0.4,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        PlanningStrategyResult result = registry.execute(context);

        assertTrue(result.isTerminal());
        assertEquals("ReAct", result.getScene());
        assertEquals(1, result.getSteps().size());
        assertEquals("REACT", result.getSteps().get(0).getStepType());
    }

    @Test
    void executeShouldPreferLowOrderWhenMultipleMatch() {
        PlanningStrategyHandler high = new TestHandler(PlanningStrategyType.REACT, 100, true, false, "high");
        PlanningStrategyHandler low = new TestHandler(PlanningStrategyType.CHAIN_OF_THOUGHT,
                1,
                true,
                true,
                "low");
        PlanningStrategyRegistry registry = new PlanningStrategyRegistry(List.of(high, low));
        PlanningStrategyContext context = new PlanningStrategyContext("plan-order",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        PlanningStrategyResult result = registry.execute(context);

        assertTrue(result.isTerminal());
        assertEquals("low", result.getScene());
    }

    @Test
    void constructorShouldThrowWhenOrderConflict() {
        PlanningStrategyHandler left = new TestHandler(PlanningStrategyType.REACT,
                20,
                true,
                false,
                "left");
        PlanningStrategyHandler right = new TestHandler(PlanningStrategyType.DEBATE,
                20,
                true,
                false,
                "right");

        assertThrows(IllegalStateException.class, () -> new PlanningStrategyRegistry(List.of(left, right)));
    }

    @Test
    void executeShouldSkipNonMatchAndReturnEmpty() {
        PlanningStrategyHandler handler = new TestHandler(PlanningStrategyType.REACT,
                10,
                false,
                false,
                "never");
        PlanningStrategyRegistry registry = new PlanningStrategyRegistry(List.of(handler));
        PlanningStrategyContext context = new PlanningStrategyContext("plan-empty",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        PlanningStrategyResult result = registry.execute(context);

        assertTrue(!result.isTerminal());
        assertEquals(0, result.getSteps().size());
    }

    @Test
    void executeShouldThrowWhenHandlerFailed() {
        PlanningStrategyHandler handler = new PlanningStrategyHandler() {
            @Override
            public PlanningStrategyType getType() {
                return PlanningStrategyType.REACT;
            }

            @Override
            public int getOrder() {
                return 1;
            }

            @Override
            public boolean matches(PlanningStrategyContext context) {
                return true;
            }

            @Override
            public PlanningStrategyResult handle(PlanningStrategyContext context) {
                throw new IllegalStateException("handler_failed");
            }
        };
        PlanningStrategyRegistry registry = new PlanningStrategyRegistry(List.of(handler));
        PlanningStrategyContext context = new PlanningStrategyContext("plan-fail",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.2,
                PlanningContextKeys.STRATEGY_SIMPLE,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);

        assertThrows(IllegalStateException.class, () -> registry.execute(context));
    }

    private static class TestHandler implements PlanningStrategyHandler {

        private final PlanningStrategyType type;
        private final int order;
        private final boolean matches;
        private final boolean terminal;
        private final String scene;

        private TestHandler(PlanningStrategyType type,
                            int order,
                            boolean matches,
                            boolean terminal,
                            String scene) {
            this.type = type;
            this.order = order;
            this.matches = matches;
            this.terminal = terminal;
            this.scene = scene;
        }

        @Override
        public PlanningStrategyType getType() {
            return type;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public boolean matches(PlanningStrategyContext context) {
            return matches;
        }

        @Override
        public PlanningStrategyResult handle(PlanningStrategyContext context) {
            return terminal
                    ? PlanningStrategyResult.terminal(new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    scene,
                    null)
                    : PlanningStrategyResult.normal(new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    scene,
                    null);
        }
    }
}
