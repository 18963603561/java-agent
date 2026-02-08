package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.config.ModelConfigProperties;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRegistry;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.contract.ModelScene;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelRouterTest {

    @Test
    void routeShouldNotFallbackWhenFallbackDisabled() {
        ModelConfigProperties properties = new ModelConfigProperties();
        ModelDefinition planner = new ModelDefinition();
        planner.setModelId("planner-model");
        ModelDefinition fallback = new ModelDefinition();
        fallback.setModelId("fallback-model");
        properties.setModels(Map.of(
                "planner-model", planner,
                "fallback-model", fallback
        ));
        properties.setRoutes(Map.of());
        properties.setFallbackModelId("fallback-model");
        properties.setFallbackEnabled(false);

        ModelRouter router = new ModelRouter(properties, new ModelRegistry(properties));
        ModelDefinition result = router.route(ModelScene.PLANNER);

        assertNull(result);
    }

    @Test
    void routeShouldUseFallbackWhenEnabled() {
        ModelConfigProperties properties = new ModelConfigProperties();
        ModelDefinition fallback = new ModelDefinition();
        fallback.setModelId("fallback-model");
        properties.setModels(Map.of("fallback-model", fallback));
        properties.setRoutes(Map.of());
        properties.setFallbackModelId("fallback-model");
        properties.setFallbackEnabled(true);

        ModelRouter router = new ModelRouter(properties, new ModelRegistry(properties));
        ModelDefinition result = router.route(ModelScene.PLANNER);

        assertNotNull(result);
        assertEquals("fallback-model", result.getModelId());
    }

    @Test
    void routeShouldUseConfiguredRouteFirst() {
        ModelConfigProperties properties = new ModelConfigProperties();
        ModelDefinition planner = new ModelDefinition();
        planner.setModelId("planner-model");
        ModelDefinition fallback = new ModelDefinition();
        fallback.setModelId("fallback-model");
        properties.setModels(Map.of(
                "planner-model", planner,
                "fallback-model", fallback
        ));
        properties.setRoutes(Map.of("planner", "planner-model"));
        properties.setFallbackModelId("fallback-model");
        properties.setFallbackEnabled(true);

        ModelRouter router = new ModelRouter(properties, new ModelRegistry(properties));
        ModelDefinition result = router.route(ModelScene.PLANNER);

        assertNotNull(result);
        assertEquals("planner-model", result.getModelId());
    }
}


