package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.pricing.CostCalculator;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRegistry;
import com.example.agent.security.auth.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 默认预算记录工厂，实现字段标准化、令牌统计回填与成本估算。
 */
@Component
public class DefaultTokenUsageRecordFactory implements TokenUsageRecordFactory {

    private final CostCalculator costCalculator;
    private final ModelRegistry modelRegistry;

    public DefaultTokenUsageRecordFactory(CostCalculator costCalculator,
                                          ModelRegistry modelRegistry) {
        this.costCalculator = costCalculator;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public TokenUsageRecord create(TokenUsageInput input, TenantContext tenantContext) {
        TokenUsageRecord record = new TokenUsageRecord();
        record.setRecordId(UUID.randomUUID().toString());
        record.setUsageId(input.getUsageId());
        record.setTaskId(input.getTaskId());
        record.setAgentId(input.getAgentId());
        record.setModel(input.getModel());
        record.setProvider(input.getProvider());

        int inputTokens = input.getInputTokens() != null ? input.getInputTokens() : 0;
        int outputTokens = input.getOutputTokens() != null ? input.getOutputTokens() : 0;
        int totalTokens = input.getTotalTokens() != null ? input.getTotalTokens() : inputTokens + outputTokens;

        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setTotalTokens(totalTokens);
        record.setTenantId(tenantContext.getTenantId());
        record.setCreatedAt(Instant.now());

        double cost = input.getCostUsd() != null ? input.getCostUsd() : 0;
        if (cost == 0 && input.getModel() != null) {
            ModelDefinition model = modelRegistry.getModel(input.getModel());
            cost = costCalculator.calculate(model, inputTokens, outputTokens);
        }
        record.setCostUsd(cost);
        return record;
    }
}
