package com.example.agent.model;

import org.springframework.stereotype.Component;

/**
 * 默认模型供应商实现，返回简化结果。
 */
@Component
public class DefaultModelProvider implements ModelProvider {

    @Override
    public ModelResponse invoke(ModelDefinition definition, String prompt) {
        String modelId = definition != null ? definition.getModelId() : "unknown";
        int inputTokens = prompt != null ? prompt.length() : 0;
        String content = "response:" + (prompt == null ? "" : prompt);
        int outputTokens = content.length();
        return new ModelResponse(modelId, content, inputTokens, outputTokens);
    }
}
