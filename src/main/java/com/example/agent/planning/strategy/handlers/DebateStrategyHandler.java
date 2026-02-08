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
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 辩论策略处理器。
 */
@Component
public class DebateStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造辩论策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public DebateStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.DEBATE;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.DEBATE;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        return context != null
                && PlanningContextKeys.STRATEGY_DEBATE.equalsIgnoreCase(context.getStrategy());
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUERY, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        appendStep(context,
                planParser.buildStepSpec("DEBATE", input),
                buildPlanStepMeta(stepKey, "DEBATE", "debate"));
        addDependency(context, stepKey);
        return normal(context, "辩论", stepKey);
    }
}
