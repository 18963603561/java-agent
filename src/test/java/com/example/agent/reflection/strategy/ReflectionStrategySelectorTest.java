package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectionStrategySelectorTest {

    @Test
    void resolveOrderedStrategiesOrdersByOrderAndEnabledFlag() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);

        ReflectionStrategy highOrderDisabled = new StubStrategy("disabled", 10, false);
        ReflectionStrategy mediumOrderEnabled = new StubStrategy("medium", 50, true);
        ReflectionStrategy lowOrderEnabled = new StubStrategy("low", 20, true);

        ReflectionStrategySelector selector = new ReflectionStrategySelector(
                properties,
                List.of(highOrderDisabled, mediumOrderEnabled, lowOrderEnabled)
        );

        List<ReflectionStrategy> ordered = selector.resolveOrderedStrategies();
        assertEquals(2, ordered.size());
        assertEquals("low", ((StubStrategy) ordered.get(0)).name());
        assertEquals("medium", ((StubStrategy) ordered.get(1)).name());
    }

    @Test
    void allowFallbackReflectsPropertyValue() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setFallbackEnabled(false);
        ReflectionStrategySelector selector = new ReflectionStrategySelector(properties, List.of());
        assertEquals(false, selector.allowFallback());
    }

    private record StubStrategy(String name, int order, boolean enabled) implements ReflectionStrategy {

        @Override
        public ReflectionDecision execute(ReflectionExecutionContext context) {
            return null;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean isEnabled(ReflectionProperties reflectionProperties) {
            return enabled;
        }
    }
}

