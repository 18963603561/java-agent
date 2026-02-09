package com.example.agent.budget.token.pricing;

import com.example.agent.capabilities.llm.provider.ModelDefinition;
import org.springframework.stereotype.Component;

/**
 * 成本计算器，根据模型单价计算令牌消耗成本。
 */
@Component
public class CostCalculator {

    /**
     * 计算模型调用成本。
     *
     * @param model 模型定义，包含输入与输出单价
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     * @return 成本（美元），当模型为空时返回 0
     */
    public double calculate(ModelDefinition model, int inputTokens, int outputTokens) {
        if (model == null) {
            return 0;
        }
        double inputCost = model.getInputCostUsd() * inputTokens;
        double outputCost = model.getOutputCostUsd() * outputTokens;
        return inputCost + outputCost;
    }
}
