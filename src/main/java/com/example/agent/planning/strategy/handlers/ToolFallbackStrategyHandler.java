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
 * 工具回退策略处理器。
 */
@Component
public class ToolFallbackStrategyHandler extends AbstractPlanningStrategyHandler {

    /**
     * 构造工具回退策略处理器。
     *
     * @param planParser 步骤解析器
     */
    public ToolFallbackStrategyHandler(PlanParser planParser) {
        super(planParser);
    }

    @Override
    public PlanningStrategyType getType() {
        return PlanningStrategyType.TOOL_FALLBACK;
    }

    @Override
    public int getOrder() {
        return PlanningStrategyOrders.TOOL_FALLBACK;
    }

    @Override
    public boolean matches(PlanningStrategyContext context) {
        return true;
    }

    @Override
    public PlanningStrategyResult handle(PlanningStrategyContext context) {
        String stepKey = nextStepKey(context);
        Map<String, Object> input = new HashMap<>();
        input.put(PlanningFieldKeys.QUERY, context.getQuery());
        input.put(PlanningFieldKeys.CONTEXT, context.getPlanningContext().mutableValues());
        input.put(PlanningFieldKeys.STEP_KEY, stepKey);
        input.put(PlanningFieldKeys.CRITICAL, context.getComplexityScore() >= 0.6);
        input.put(PlanningContextKeys.STRATEGY, context.getCognitiveStrategy());
        String toolName = context.getPlanningContext().getString(PlanningContextKeys.TOOL);
        if (toolName != null) {
            input.put(PlanningContextKeys.TOOL, toolName);
        }
        String fallbackTool = context.getPlanningContext().getString(PlanningContextKeys.FALLBACK_TOOL);
        if (fallbackTool != null) {
            input.put(PlanningContextKeys.FALLBACK_TOOL, fallbackTool);
        }
        if (context.getPreviousStepKey() != null) {
            input.put(PlanningFieldKeys.DEPENDS_ON, List.of(context.getPreviousStepKey()));
        }
        appendStep(context,
                planParser.buildStepSpec("TOOL", input),
                buildPlanStepMeta(stepKey, "TOOL", "tool-exec"));
        addDependency(context, stepKey);
        return terminal(context, "规则回退", stepKey);
    }
}
