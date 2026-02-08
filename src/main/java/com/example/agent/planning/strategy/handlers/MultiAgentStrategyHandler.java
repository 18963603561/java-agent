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
 * 多智能体策略处理器。
 */
@Component
public class MultiAgentStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造多智能体策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public MultiAgentStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.MULTI_AGENT;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.MULTI_AGENT;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        return context != null && equalsAnyIgnoreCase(context.getStrategy(), List.of(
                PlanningContextKeys.STRATEGY_MULTI_AGENT,
                PlanningContextKeys.STRATEGY_MULTI_AGENT_ALIAS));
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUERY, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        appendStep(context,
                planParser.buildStepSpec("MULTI_AGENT", input),
                buildPlanStepMeta(stepKey, "MULTI_AGENT", "multi-agent"));
        addDependency(context, stepKey);
        return normal(context, "多智能体", stepKey);
    }
}
