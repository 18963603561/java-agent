package com.example.agent.budget;

import com.example.agent.model.ModelDefinition;
import org.springframework.stereotype.Component;

/**
 * 成本计算器，根据模型单价计算消耗。
 */
@Component
public class CostCalculator {

    public double calculate(ModelDefinition model, int inputTokens, int outputTokens) {
        if (model == null) {
            return 0;
        }
        double inputCost = model.getInputCostUsd() * inputTokens;
        double outputCost = model.getOutputCostUsd() * outputTokens;
        return inputCost + outputCost;
    }
}
