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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 策略注册顺序稳定性测试。
 */
class PlanningStrategyRegistryOrderingStabilityTest {

    private final PlanParser planParser = new PlanParser(new ObjectMapper());

    @Test
    void executeShouldRemainStableWithRandomRegistrationOrder() {
        List<PlanningStrategyHandler> baselineHandlers = buildHandlers();
        String expectedStepType = executeWithHandlers(baselineHandlers);

        for (int round = 0; round < 20; round++) {
            List<PlanningStrategyHandler> shuffled = new ArrayList<>(buildHandlers());
            Collections.shuffle(shuffled, new Random(1000L + round));
            String actualStepType = executeWithHandlers(shuffled);
            assertEquals(expectedStepType, actualStepType);
        }
    }

    private String executeWithHandlers(List<PlanningStrategyHandler> handlers) {
        PlanningStrategyRegistry registry = new PlanningStrategyRegistry(handlers);
        PlanningStrategyContext context = new PlanningStrategyContext("plan-random",
                "t-1",
                "query",
                new PlanningContext(new HashMap<>()),
                0.9,
                PlanningContextKeys.STRATEGY_TREE_OF_THOUGHTS,
                PlanningContextKeys.EXECUTION_SEQUENTIAL,
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);
        PlanningStrategyResult result = registry.execute(context);
        return result.getSteps().isEmpty() ? "" : result.getSteps().get(0).getStepType();
    }

    private List<PlanningStrategyHandler> buildHandlers() {
        return List.of(
                new ChainOfThoughtStrategyHandler(planParser),
                new ThoughtTreeStrategyHandler(planParser),
                new MultiAgentStrategyHandler(planParser),
                new DebateStrategyHandler(planParser),
                new ResearchStrategyHandler(planParser),
                new ReactStrategyHandler(planParser),
                new DirectLlmStrategyHandler(planParser),
                new ToolFallbackStrategyHandler(planParser)
        );
    }
}

