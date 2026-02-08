package com.example.agent.planning.strategy.handlers;

import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.planning.strategy.AbstractPlanningStrategyHandler;
import com.example.agent.planning.strategy.PlanningStrategyContext;
import com.example.agent.planning.strategy.PlanningStrategyOrders;
import com.example.agent.planning.strategy.PlanningStrategyResult;
import com.example.agent.planning.strategy.PlanningStrategyType;
import com.example.agent.planning.parser.PlanParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 链式推理策略处理器。
 */
@Component
public class ChainOfThoughtStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造链式推理策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public ChainOfThoughtStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.CHAIN_OF_THOUGHT;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.CHAIN_OF_THOUGHT;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        if (context == null) {
            return false;
        }
        return isChainOfThoughtValue(context.getMode())
                || isChainOfThoughtValue(context.getStrategy())
                || isChainOfThoughtValue(context.getCognitiveStrategy());
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUESTION, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        appendStep(context,
                planParser.buildStepSpec("CHAIN_OF_THOUGHT", input),
                buildPlanStepMeta(stepKey, "CHAIN_OF_THOUGHT", "chain-of-thought"));
        return terminal(context, "链式推理", stepKey);
    }

    private boolean isChainOfThoughtValue(String value) {
        return equalsAnyIgnoreCase(value, List.of(
                PlanningContextKeys.MODE_COT,
                PlanningContextKeys.STRATEGY_CHAIN_OF_THOUGHT,
                PlanningContextKeys.STRATEGY_CHAIN_OF_THOUGHT_ALIAS));
    }
}
