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
 * 深度研究策略处理器。
 */
@Component
public class ResearchStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造深度研究策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public ResearchStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.RESEARCH;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.RESEARCH;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        if (context == null) {
            return false;
        }
        return equalsAnyIgnoreCase(context.getMode(), List.of(PlanningContextKeys.MODE_DEEP_RESEARCH))
                || equalsAnyIgnoreCase(context.getStrategy(), List.of(PlanningContextKeys.STRATEGY_RESEARCH));
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUERY, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        appendStep(context,
                planParser.buildStepSpec("RESEARCH", input),
                buildPlanStepMeta(stepKey, "RESEARCH", "deep-research"));
        addDependency(context, stepKey);
        return normal(context, "深度研究", stepKey);
    }
}
