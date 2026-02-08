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
 * ReAct 策略处理器。
 */
@Component
public class ReactStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造 ReAct 策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public ReactStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.REACT;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.REACT;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        if (context == null) {
            return false;
        }
        if (PlanningContextKeys.MODE_REACT.equalsIgnoreCase(context.getMode())
                || PlanningContextKeys.MODE_REACT.equalsIgnoreCase(context.getStrategy())) {
            return true;
        }
        Boolean react = context.getPlanningContext().getBoolean(PlanningContextKeys.REACT);
        if (react == null) {
            react = context.getPlanningContext().getBoolean(PlanningContextKeys.REACT_ENABLED);
        }
        return Boolean.TRUE.equals(react);
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUESTION, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        input.put(PlanningContextKeys.STRATEGY, context.getCognitiveStrategy());
        appendStep(context,
                planParser.buildStepSpec("REACT", input),
                buildPlanStepMeta(stepKey, "REACT", "react"));
        addDependency(context, stepKey);
        return terminal(context, "ReAct", stepKey);
    }
}
