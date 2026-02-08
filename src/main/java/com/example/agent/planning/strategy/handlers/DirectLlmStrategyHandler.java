package com.example.agent.planning.strategy.handlers;

import com.example.agent.capabilities.llm.contract.ModelToolChoice;
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
 * 大模型直答策略处理器。
 */
@Component
public class DirectLlmStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造大模型直答策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public DirectLlmStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.DIRECT_LLM;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.DIRECT_LLM;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        if (context == null) {
            return false;
        }
        Boolean disableTools = context.getPlanningContext().getBoolean(PlanningContextKeys.DISABLE_TOOLS);
        if (Boolean.TRUE.equals(disableTools)) {
            return true;
        }
        ModelToolChoice toolChoice = context.getPlanningContext().getToolChoice();
        return toolChoice != null && toolChoice.getMode() == ModelToolChoice.Mode.NONE;
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
                planParser.buildStepSpec("LLM", input),
                buildPlanStepMeta(stepKey, "LLM", "direct-llm"));
        addDependency(context, stepKey);
        return terminal(context, "大模型直答", stepKey);
    }
}
