package com.example.agent.budget;

import com.example.agent.budget.token.application.DefaultTokenUsageRecordFactory;
import com.example.agent.budget.token.model.TokenUsageInput;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.pricing.CostCalculator;
import com.example.agent.capabilities.llm.config.ModelConfigProperties;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRegistry;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultTokenUsageRecordFactoryTest {

    private static final double DELTA = 1e-9;

    @Test
    void createShouldCalculateCostFromModelPricing() {
        DefaultTokenUsageRecordFactory factory = new DefaultTokenUsageRecordFactory(
                new CostCalculator(),
                buildRegistry("gpt-4", 0.000001, 0.000002));

        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId("usage-1");
        input.setTaskId("task-1");
        input.setAgentId("agent-1");
        input.setModel("gpt-4");
        input.setProvider("openai");
        input.setInputTokens(1000);
        input.setOutputTokens(500);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req-1", "trace-1");

        TokenUsageRecord record = factory.create(input, tenantContext);

        assertNotNull(record.getRecordId());
        assertEquals("usage-1", record.getUsageId());
        assertEquals("task-1", record.getTaskId());
        assertEquals("agent-1", record.getAgentId());
        assertEquals("gpt-4", record.getModel());
        assertEquals("openai", record.getProvider());
        assertEquals(1000, record.getInputTokens());
        assertEquals(500, record.getOutputTokens());
        assertEquals(1500, record.getTotalTokens());
        assertEquals("tenant-a", record.getTenantId());
        assertEquals(0.002, record.getCostUsd(), DELTA);
        assertNotNull(record.getCreatedAt());
    }

    @Test
    void createShouldUseInputCostWhenProvided() {
        DefaultTokenUsageRecordFactory factory = new DefaultTokenUsageRecordFactory(
                new CostCalculator(),
                buildRegistry("gpt-4", 0.000001, 0.000002));

        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId("usage-2");
        input.setTaskId("task-2");
        input.setAgentId("agent-2");
        input.setModel("gpt-4");
        input.setProvider("openai");
        input.setInputTokens(300);
        input.setOutputTokens(200);
        input.setCostUsd(9.99);

        TenantContext tenantContext = new TenantContext("tenant-b", "user-b", List.of(), "req-2", "trace-2");

        TokenUsageRecord record = factory.create(input, tenantContext);

        assertEquals(9.99, record.getCostUsd(), DELTA);
    }

    private ModelRegistry buildRegistry(String modelId, double inputCost, double outputCost) {
        ModelDefinition definition = new ModelDefinition();
        definition.setModelId(modelId);
        definition.setInputCostUsd(inputCost);
        definition.setOutputCostUsd(outputCost);

        ModelConfigProperties properties = new ModelConfigProperties();
        Map<String, ModelDefinition> models = new HashMap<>();
        models.put(modelId, definition);
        properties.setModels(models);
        return new ModelRegistry(properties);
    }
}


