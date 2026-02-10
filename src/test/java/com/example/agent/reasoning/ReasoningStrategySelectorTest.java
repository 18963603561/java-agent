package com.example.agent.reasoning;

import com.example.agent.reasoning.common.config.ReasoningConfigResolver;
import com.example.agent.reasoning.common.config.ReasoningConfigValidator;
import com.example.agent.reasoning.common.config.ReasoningExecutionProperties;
import com.example.agent.reasoning.common.selection.ReasoningDegradePolicy;
import com.example.agent.reasoning.common.selection.ReasoningStrategySelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningStrategySelectorTest {

    private final ReasoningConfigResolver configResolver = new ReasoningConfigResolver(
            new ReasoningExecutionProperties(),
            new ReasoningConfigValidator()
    );
    private final ReasoningStrategySelector selector = new ReasoningStrategySelector(configResolver);
    private final ReasoningDegradePolicy degradePolicy = new ReasoningDegradePolicy(configResolver);

    @Test
    void selectPrimaryShouldUsePreferredWhenValid() {
        String selected = selector.selectPrimary("DEBATE", Map.of("highRisk", true));
        assertEquals("debate", selected);
    }

    @Test
    void selectPrimaryShouldChooseThoughtTreeForHighRisk() {
        String selected = selector.selectPrimary(null, Map.of("highRisk", true));
        assertEquals("thought_tree", selected);
    }

    @Test
    void selectPrimaryShouldDefaultToCot() {
        String selected = selector.selectPrimary(null, Map.of("unknown", true));
        assertEquals("cot", selected);
    }

    @Test
    void degradePolicyShouldStartWithPrimary() {
        List<String> order = degradePolicy.resolveFallbackOrder("thought_tree");
        assertEquals("thought_tree", order.get(0));
        assertTrue(order.contains("cot"));
        assertTrue(order.contains("debate"));
    }
}
