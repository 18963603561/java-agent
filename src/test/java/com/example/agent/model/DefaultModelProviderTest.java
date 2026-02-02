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

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertFalse(body.containsKey("tools"));
        assertFalse(body.containsKey("tool_choice"));
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

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertTrue(body.containsKey("tools"));
        Object toolsObj = body.get("tools");
        assertTrue(toolsObj instanceof List<?>);
        List<?> tools = (List<?>) toolsObj;
        assertEquals(1, tools.size());
        assertTrue(tools.get(0) instanceof Map<?, ?>);
        Map<?, ?> toolMap = (Map<?, ?>) tools.get(0);
        assertEquals("function", toolMap.get("type"));
        assertTrue(toolMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> functionMap = (Map<?, ?>) toolMap.get("function");
        assertEquals("demo_tool", functionMap.get("name"));
        assertEquals("demo", functionMap.get("description"));
        assertEquals(parameters, functionMap.get("parameters"));
    }

    @Test
    void buildOpenAiRequestBodyIncludesSpecifiedToolChoice() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelToolChoice toolChoice = ModelToolChoice.specified("demo_tool");

        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));
        request.setToolChoice(toolChoice);

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertTrue(body.containsKey("tool_choice"));
        assertTrue(body.containsKey("toolChoice"));
        Object snakeCaseChoice = body.get("tool_choice");
        assertTrue(snakeCaseChoice instanceof Map<?, ?>);
        Map<?, ?> snakeChoiceMap = (Map<?, ?>) snakeCaseChoice;
        assertEquals("function", snakeChoiceMap.get("type"));
        assertTrue(snakeChoiceMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> snakeFunction = (Map<?, ?>) snakeChoiceMap.get("function");
        assertNotNull(snakeFunction.get("name"));
        assertEquals("demo_tool", snakeFunction.get("name"));
        Object toolChoiceObj = body.get("toolChoice");
        assertTrue(toolChoiceObj instanceof Map<?, ?>);
        Map<?, ?> toolChoiceMap = (Map<?, ?>) toolChoiceObj;
        assertEquals("function", toolChoiceMap.get("type"));
        assertTrue(toolChoiceMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> camelFunction = (Map<?, ?>) toolChoiceMap.get("function");
        assertNotNull(camelFunction.get("name"));
        assertEquals("demo_tool", camelFunction.get("name"));
    }

    @Test
    void buildOpenAiRequestBodyDefaultsParametersWhenMissing() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);

        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertTrue(body.containsKey("tools"));
        Object toolsObj = body.get("tools");
        assertTrue(toolsObj instanceof List<?>);
        List<?> tools = (List<?>) toolsObj;
        assertEquals(1, tools.size());
        assertTrue(tools.get(0) instanceof Map<?, ?>);
        Map<?, ?> toolMap = (Map<?, ?>) tools.get(0);
        assertEquals("function", toolMap.get("type"));
        assertTrue(toolMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> functionMap = (Map<?, ?>) toolMap.get("function");
        assertTrue(functionMap.get("parameters") instanceof Map<?, ?>);
        Map<?, ?> parameters = (Map<?, ?>) functionMap.get("parameters");
        assertEquals("object", parameters.get("type"));
        assertTrue(parameters.get("properties") instanceof Map<?, ?>);
        Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
        assertTrue(properties.isEmpty());
    }

    @Test
    void buildOpenAiRequestBodyUsesMessagesWhenProvided() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setMessages(List.of(
                new PromptMessage(PromptRole.SYSTEM, "system"),
                new PromptMessage(PromptRole.USER, "user")
        ));

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertTrue(body.containsKey("messages"));
        Object messagesObj = body.get("messages");
        assertTrue(messagesObj instanceof List<?>);
        List<?> messages = (List<?>) messagesObj;
        assertEquals(2, messages.size());
    }

    @Test
    void buildOllamaRequestBodyOmitsToolsWhenChoiceNone() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));
        request.setToolChoice(ModelToolChoice.none());

        Map<String, Object> body = provider.buildOllamaRequestBody("model-x", request);

        assertFalse(body.containsKey("tools"));
    }

    @Test
    void buildOllamaRequestBodyFiltersSpecifiedTool() {
        ObjectMapper objectMapper = new ObjectMapper();
        DefaultModelProvider provider = new DefaultModelProvider(objectMapper, WebClient.builder());
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelToolDefinition other = new ModelToolDefinition("other_tool", "other", null);
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool, other));
        request.setToolChoice(ModelToolChoice.specified("demo_tool"));

        Map<String, Object> body = provider.buildOllamaRequestBody("model-x", request);

        assertTrue(body.containsKey("tools"));
        Object toolsObj = body.get("tools");
        assertTrue(toolsObj instanceof List<?>);
        List<?> tools = (List<?>) toolsObj;
        assertEquals(1, tools.size());
        assertTrue(tools.get(0) instanceof Map<?, ?>);
        Map<?, ?> toolMap = (Map<?, ?>) tools.get(0);
        assertEquals("function", toolMap.get("type"));
        assertTrue(toolMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> functionMap = (Map<?, ?>) toolMap.get("function");
        assertEquals("demo_tool", functionMap.get("name"));
    }

    @Test
    void buildOllamaRequestBodyMapsDeveloperRoleToSystem() {
        DefaultModelProvider provider = new DefaultModelProvider(new ObjectMapper(), WebClient.builder());
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setMessages(List.of(
                new PromptMessage(PromptRole.DEVELOPER, "dev"),
                new PromptMessage(PromptRole.USER, "user")
        ));

        Map<String, Object> body = provider.buildOllamaRequestBody("model-x", request);

        assertTrue(body.containsKey("messages"));
        Object messagesObj = body.get("messages");
        assertTrue(messagesObj instanceof List<?>);
        List<?> messages = (List<?>) messagesObj;
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof Map<?, ?>);
        Map<?, ?> first = (Map<?, ?>) messages.get(0);
        assertEquals("system", first.get("role"));
    }
}
