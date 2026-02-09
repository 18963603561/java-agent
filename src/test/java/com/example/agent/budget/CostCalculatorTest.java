package com.example.agent.budget;

import com.example.agent.budget.token.pricing.CostCalculator;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostCalculatorTest {

    private static final double DELTA = 1e-9;

    @Test
    void calculateShouldReturnZeroWhenModelIsNull() {
        CostCalculator calculator = new CostCalculator();

        double result = calculator.calculate(null, 100, 200);

        assertEquals(0.0, result, DELTA);
    }

    @Test
    void calculateShouldReturnZeroWhenTokensAreZero() {
        CostCalculator calculator = new CostCalculator();
        ModelDefinition model = buildModel(0.0001, 0.0002);

        double result = calculator.calculate(model, 0, 0);

        assertEquals(0.0, result, DELTA);
    }

    @Test
    void calculateShouldHandleLargeTokenVolume() {
        CostCalculator calculator = new CostCalculator();
        ModelDefinition model = buildModel(0.000003, 0.000012);

        double result = calculator.calculate(model, 1_000_000, 800_000);

        assertEquals(12.6, result, DELTA);
    }

    @Test
    void calculateShouldKeepRatePrecision() {
        CostCalculator calculator = new CostCalculator();
        ModelDefinition model = buildModel(0.0000015, 0.0000023);

        double result = calculator.calculate(model, 333, 777);

        assertEquals(0.0022866, result, DELTA);
    }

    private ModelDefinition buildModel(double inputCost, double outputCost) {
        ModelDefinition definition = new ModelDefinition();
        definition.setInputCostUsd(inputCost);
        definition.setOutputCostUsd(outputCost);
        return definition;
    }
}
