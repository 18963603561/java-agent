package com.example.agent.model;

import com.example.agent.capabilities.llm.provider.DefaultModelProvider;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.contract.ModelToolDefinition;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import com.example.agent.capabilities.llm.provider.LocalFallbackStrategy;
import com.example.agent.capabilities.llm.config.ModelProviderHttpProperties;
import com.example.agent.capabilities.llm.provider.ModelRequestBodyBuilder;
import com.example.agent.capabilities.llm.provider.ModelMessageBuilder;
import com.example.agent.capabilities.llm.provider.ProviderRouter;
import com.example.agent.capabilities.llm.provider.ToolPayloadBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelProviderTest {

    private DefaultModelProvider provider;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ModelProviderHttpProperties properties = new ModelProviderHttpProperties();
        ModelMessageBuilder messageBuilder = new ModelMessageBuilder();
        ToolPayloadBuilder toolPayloadBuilder = new ToolPayloadBuilder();
        ModelRequestBodyBuilder requestBodyBuilder = new ModelRequestBodyBuilder(properties, messageBuilder, toolPayloadBuilder);
        LocalFallbackStrategy localFallbackStrategy = new LocalFallbackStrategy(objectMapper, messageBuilder);
        ProviderRouter providerRouter = new ProviderRouter(List.of());
        provider = new DefaultModelProvider(providerRouter, localFallbackStrategy, requestBodyBuilder);
    }

    @Test
    void buildOpenAiRequestBodyOmitsToolsWhenEmpty() {
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
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelToolChoice toolChoice = ModelToolChoice.specified("demo_tool");

        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));
        request.setToolChoice(toolChoice);

        Map<String, Object> body = provider.buildOpenAiRequestBody("model-x", request, null);

        assertTrue(body.containsKey("tool_choice"));
        assertFalse(body.containsKey("toolChoice"));
        Object snakeCaseChoice = body.get("tool_choice");
        assertTrue(snakeCaseChoice instanceof Map<?, ?>);
        Map<?, ?> snakeChoiceMap = (Map<?, ?>) snakeCaseChoice;
        assertEquals("function", snakeChoiceMap.get("type"));
        assertTrue(snakeChoiceMap.get("function") instanceof Map<?, ?>);
        Map<?, ?> snakeFunction = (Map<?, ?>) snakeChoiceMap.get("function");
        assertNotNull(snakeFunction.get("name"));
        assertEquals("demo_tool", snakeFunction.get("name"));
    }

    @Test
    void buildOpenAiRequestBodyDefaultsParametersWhenMissing() {
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
        ModelToolDefinition tool = new ModelToolDefinition("demo_tool", "demo", null);
        ModelRequest request = new ModelRequest("ping", ModelScene.CHEAP);
        request.setTools(List.of(tool));
        request.setToolChoice(ModelToolChoice.none());

        Map<String, Object> body = provider.buildOllamaRequestBody("model-x", request);

        assertFalse(body.containsKey("tools"));
    }

    @Test
    void buildOllamaRequestBodyFiltersSpecifiedTool() {
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

    @Test
    void invokeFallsBackToLocalWhenNoAdapterMatched() {
        ModelRequest request = new ModelRequest("PLAN_CONTEXT_JSON:{}", ModelScene.CHEAP);
        ModelDefinition definition = new ModelDefinition();
        definition.setModelId("local-model");

        ModelResponse response = provider.invoke(definition, request);

        assertNotNull(response);
        assertEquals("local-model", response.getModelId());
        assertTrue(response.getContent().contains("local-plan") || response.getContent().startsWith("response:"));
    }
}


