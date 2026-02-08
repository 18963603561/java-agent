package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.provider.LocalFallbackStrategy;
import com.example.agent.capabilities.llm.provider.ModelMessageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalFallbackStrategy 回归测试。
 */
class LocalFallbackStrategyTest {

    private LocalFallbackStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LocalFallbackStrategy(new ObjectMapper(), new ModelMessageBuilder());
    }

    @Test
    void buildResponseShouldRoutePlanMarker() {
        ModelRequest request = new ModelRequest("PLAN_CONTEXT_JSON:{\"query\":\"test\"}", ModelScene.CHEAP);

        String result = strategy.buildResponse(request);

        assertTrue(result.contains("local-plan"));
    }

    @Test
    void buildResponseShouldRouteReflectionMarker() {
        ModelRequest request = new ModelRequest("REFLECTION_CONTEXT_JSON:{\"output\":\"ok\"}", ModelScene.CHEAP);

        String result = strategy.buildResponse(request);

        assertTrue(result.contains("score"));
        assertTrue(result.contains("retry"));
    }

    @Test
    void buildResponseShouldRouteToolResultMarkerFirst() {
        String prompt = "LLM_STEP_TOOL_RESULT_JSON:{\"status\":\"SUCCESS\",\"tool\":\"demo_tool\"}\n"
                + "PLAN_CONTEXT_JSON:{\"query\":\"ignored\"}";
        ModelRequest request = new ModelRequest(prompt, ModelScene.CHEAP);

        String result = strategy.buildResponse(request);

        assertTrue(result.contains("highlights"));
        assertTrue(result.contains("status=SUCCESS"));
    }

    @Test
    void buildResponseShouldFallbackToEchoWhenNoMarker() {
        ModelRequest request = new ModelRequest("plain prompt", ModelScene.CHEAP);

        String result = strategy.buildResponse(request);

        assertEquals("response:plain prompt", result);
    }
}
