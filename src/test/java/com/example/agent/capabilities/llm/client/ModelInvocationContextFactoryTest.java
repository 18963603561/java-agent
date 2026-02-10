package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.config.ModelConfigProperties;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRegistry;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelInvocationContextFactoryTest {

    @Test
    void createShouldNormalizeScenePhaseAndMetadata() {
        ModelInvocationContextFactory factory = createFactory("model-cheap", "openai");
        ModelRequest request = new ModelRequest("hello", null);
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", java.util.List.of(), "req-a", "trace-a");

        ModelInvocationContext context = factory.create(request, null, null, tenantContext, Map.of());

        assertNotNull(context);
        assertEquals(ModelScene.CHEAP, context.getScene());
        assertEquals("unknown", context.getPhase());
        assertEquals("trace-a", context.getTraceId());
        assertEquals("model-cheap", context.getModelId());
        assertEquals("openai", context.getProvider());
        assertEquals("CHEAP", context.getMetadata().get("scene"));
        assertEquals("openai", context.getMetadata().get("provider"));
        assertNotNull(context.getPromptTrace());
        assertEquals("unknown", context.getPromptTrace().getPromptScene());
        assertTrue(context.getStartNs() > 0);
    }

    @Test
    void createShouldRespectPromptSceneFromMetadata() {
        ModelInvocationContextFactory factory = createFactory("model-plan", "openai");
        ModelRequest request = new ModelRequest("planner", ModelScene.PLANNER);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptScene", "custom_scene");

        ModelInvocationContext context = factory.create(request, ModelScene.PLANNER, "plan", null, metadata);

        assertNotNull(context);
        assertEquals("plan", context.getPhase());
        assertEquals("custom_scene", context.getPromptTrace().getPromptScene());
    }

    @Test
    void createShouldFallbackWhenRouteMissing() {
        ModelInvocationContextFactory factory = createFactory(null, null);

        ModelInvocationContext context = factory.create(new ModelRequest("x", ModelScene.CHEAP),
                ModelScene.CHEAP,
                "plan",
                null,
                Map.of());

        assertNotNull(context);
        assertNull(context.getModelDefinition());
        assertNull(context.getModelId());
        assertNull(context.getProvider());
    }

    private ModelInvocationContextFactory createFactory(String modelId, String provider) {
        ModelConfigProperties properties = new ModelConfigProperties();
        Map<String, ModelDefinition> models = new HashMap<>();
        if (modelId != null) {
            ModelDefinition definition = new ModelDefinition();
            definition.setModelId(modelId);
            definition.setProvider(provider);
            models.put(modelId, definition);
            properties.getRoutes().put("cheap", modelId);
            properties.getRoutes().put("planner", modelId);
        }
        properties.setModels(models);
        ModelRegistry registry = new ModelRegistry(properties);
        ModelRouter router = new ModelRouter(properties, registry);
        return new ModelInvocationContextFactory(router, new ValidationSupport());
    }
}

