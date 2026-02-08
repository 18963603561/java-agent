package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.config.ModelConfigProperties;
import com.example.agent.capabilities.llm.provider.ModelFallbackDecision;
import com.example.agent.capabilities.llm.provider.ModelFallbackPolicy;
import com.example.agent.security.auth.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelFallbackPolicyTest {

    @Test
    void evaluateShouldReturnNullWhenFallbackDisabled() {
        ModelConfigProperties properties = new ModelConfigProperties();
        properties.setFallbackEnabled(false);
        properties.setFallbackModelId("fallback-model");

        ModelFallbackPolicy policy = new ModelFallbackPolicy(properties);
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req", "trace");

        ModelFallbackDecision decision = policy.evaluate(tenantContext, "task-1", "model-a", "budget_threshold");

        assertNull(decision);
    }

    @Test
    void evaluateShouldReturnNullWhenTenantContextMissing() {
        ModelConfigProperties properties = new ModelConfigProperties();
        properties.setFallbackEnabled(true);
        properties.setFallbackModelId("fallback-model");

        ModelFallbackPolicy policy = new ModelFallbackPolicy(properties);
        ModelFallbackDecision decision = policy.evaluate(null, "task-1", "model-a", "budget_threshold");

        assertNull(decision);
    }

    @Test
    void evaluateShouldCreateDecisionWhenEnabledAndDifferentModel() {
        ModelConfigProperties properties = new ModelConfigProperties();
        properties.setFallbackEnabled(true);
        properties.setFallbackModelId("fallback-model");

        ModelFallbackPolicy policy = new ModelFallbackPolicy(properties);
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req", "trace");

        ModelFallbackDecision decision = policy.evaluate(tenantContext, "task-1", "model-a", "budget_threshold");

        assertNotNull(decision);
        assertEquals("tenant-1", decision.getTenantId());
        assertEquals("task-1", decision.getTaskId());
        assertEquals("model-a", decision.getFromModel());
        assertEquals("fallback-model", decision.getToModel());
        assertEquals("budget_threshold", decision.getReason());
    }
}
