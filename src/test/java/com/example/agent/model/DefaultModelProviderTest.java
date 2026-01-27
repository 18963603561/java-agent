package com.example.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelProviderTest {

    @Test
    void buildOpenAiRequestBodyOmitsToolsWhenEmpty() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of());
        request.setToolChoice(ModelToolChoice.auto());

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request);

        assertFalse(body.containsKey("tools"));
        assertFalse(body.containsKey("toolChoice"));
    }

    @Test
    void buildOpenAiRequestBodyIncludesToolsAndParameters() {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultModelProvider provider = new DefaultModelProvider(objectMapper, WebClient.builder());
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string"))
        );
        JsonNode parameters = objectMapper.valueToTree(inputSchema);
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", parameters);

        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request);

        assertTrue(body.containsKey("tools"));
        Object toolsObj = body.get("tools");
        assertTrue(toolsObj instanceof List<?>);
        List<?> tools = (List<?>) toolsObj;
        assertEquals(1, tools.size());
        assertTrue(tools.get(0) instanceof ModelToolDefinition);
        ModelToolDefinition resolved = (ModelToolDefinition) tools.get(0);
        assertEquals("demo_tool", resolved.getName());
        assertEquals(parameters, resolved.getParameters());
    }

    @Test
    void buildOpenAiRequestBodyIncludesSpecifiedToolChoice() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelToolChoice toolChoice = ModelToolChoice.specified("demo_tool");

        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));
        request.setToolChoice(toolChoice);

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request);

        assertTrue(body.containsKey("toolChoice"));
        Object toolChoiceObj = body.get("toolChoice");
        assertTrue(toolChoiceObj instanceof Map<?, ?>);
        Map<?, ?> toolChoiceMap = (Map<?, ?>) toolChoiceObj;
        assertNotNull(toolChoiceMap.get("name"));
        assertEquals("demo_tool", toolChoiceMap.get("name"));
    }
}
