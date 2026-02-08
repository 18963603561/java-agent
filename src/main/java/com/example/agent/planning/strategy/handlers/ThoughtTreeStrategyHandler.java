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
 * 思维树策略处理器。
 */
@Component
public class ThoughtTreeStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造思维树策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public ThoughtTreeStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.THOUGHT_TREE;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.THOUGHT_TREE;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        if (context == null || context.getCognitiveStrategy() == null) {
            return false;
        }
        return equalsAnyIgnoreCase(context.getCognitiveStrategy(), List.of(
                PlanningContextKeys.STRATEGY_TREE_OF_THOUGHTS,
                PlanningContextKeys.STRATEGY_TOT))
                || context.getComplexityScore() >= 0.8;
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> thoughtInput = new HashMap<>();
        thoughtInput.put(PlanningFieldKeys.PROMPT, context.getQuery());
        thoughtInput.put(PlanningFieldKeys.STEP_KEY, stepKey);
        thoughtInput.put(PlanningFieldKeys.CRITICAL, Boolean.TRUE);
        thoughtInput.put(PlanningContextKeys.STRATEGY, context.getCognitiveStrategy());
        appendStep(context,
                planParser.buildStepSpec("THOUGHT_TREE", thoughtInput),
                buildPlanStepMeta(stepKey, "THOUGHT_TREE", "thought-tree"));
        addDependency(context, stepKey);
        return normal(context, "思维树", stepKey);
    }
}
