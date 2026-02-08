package com.example.agent.planning.capability;

import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.context.PlanningContext;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 规划推荐策略映射器。
 *
 * <p>用途：将能力评估推荐策略统一映射到规划上下文字段，避免映射逻辑散落。
 */
@Component
public class PlanningRecommendationMapper {

    /**
     * 将推荐策略写入规划上下文。
     *
     * @param planningContext 规划上下文
     * @param strategy 推荐策略文本
     */
    public void mapStrategyToContext(PlanningContext planningContext, String strategy) {
        if (planningContext == null || strategy == null) {
            return;
        }
        String normalized = strategy.toLowerCase(Locale.ROOT);
        if (normalized.contains("tree") || normalized.contains("tot")) {
            planningContext.put(PlanningContextKeys.COGNITIVE_STRATEGY_LEGACY,
                    PlanningContextKeys.STRATEGY_TREE_OF_THOUGHTS);
            return;
        }
        if (normalized.contains("debate")) {
            planningContext.put(PlanningContextKeys.STRATEGY, PlanningContextKeys.STRATEGY_DEBATE);
            return;
        }
        if (normalized.contains("research")) {
            planningContext.put(PlanningContextKeys.MODE, PlanningContextKeys.MODE_DEEP_RESEARCH);
            planningContext.put(PlanningContextKeys.STRATEGY, PlanningContextKeys.STRATEGY_RESEARCH);
            return;
        }
        if (normalized.contains("react")) {
            planningContext.put(PlanningContextKeys.REACT, true);
        }
    }
}

