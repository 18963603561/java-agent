package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.ModelDefinition;
import com.example.agent.capabilities.llm.ModelRequest;
import com.example.agent.capabilities.llm.ModelToolDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 模型请求体构造器。
 *
 * <p>用途：统一构建不同提供商所需请求体。</p>
 */
@Component
public class ModelRequestBodyBuilder {

    private final ModelProviderHttpProperties httpProperties;
    private final ModelMessageBuilder messageBuilder;
    private final ToolPayloadBuilder toolPayloadBuilder;

    public ModelRequestBodyBuilder(ModelProviderHttpProperties httpProperties,
                                   ModelMessageBuilder messageBuilder,
                                   ToolPayloadBuilder toolPayloadBuilder) {
        this.httpProperties = httpProperties;
        this.messageBuilder = messageBuilder;
        this.toolPayloadBuilder = toolPayloadBuilder;
    }

    /**
     * 构建兼容接口请求体。
     *
     * @param modelId 模型标识
     * @param request 模型请求
     * @param definition 模型定义
     * @return 请求体
     */
    public Map<String, Object> buildOpenAiRequestBody(String modelId, ModelRequest request, ModelDefinition definition) {
        String prompt = messageBuilder.resolvePrompt(request != null ? request.getPrompt() : null,
                request != null ? request.getMessages() : null);
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelId);
        Double temperatureOverride = request != null ? request.getTemperature() : null;
        body.put("temperature", temperatureOverride != null ? temperatureOverride : httpProperties.getTemperature());
        body.put("messages", messageBuilder.buildOpenAiMessages(definition,
                request != null ? request.getMessages() : null,
                prompt));
        List<ModelToolDefinition> tools = request != null ? request.getTools() : null;
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolPayload = toolPayloadBuilder.buildOpenAiTools(tools);
            if (!toolPayload.isEmpty()) {
                body.put("tools", toolPayload);
                Object toolChoice = toolPayloadBuilder.buildToolChoiceValue(request.getToolChoice());
                if (toolChoice != null) {
                    body.put("tool_choice", toolChoice);
                    body.put("toolChoice", toolChoice);
                }
            }
        }
        return body;
    }

    /**
     * 构建原生接口请求体。
     *
     * @param modelId 模型标识
     * @param request 模型请求
     * @return 请求体
     */
    public Map<String, Object> buildOllamaRequestBody(String modelId, ModelRequest request) {
        String prompt = messageBuilder.resolvePrompt(request != null ? request.getPrompt() : null,
                request != null ? request.getMessages() : null);
        Map<String, Object> body = new HashMap<>();
        body.put("model", modelId);
        body.put("messages", messageBuilder.buildOllamaMessages(request != null ? request.getMessages() : null, prompt));
        body.put("stream", false);
        Map<String, Object> options = new HashMap<>();
        Double temperatureOverride = request != null ? request.getTemperature() : null;
        options.put("temperature", temperatureOverride != null ? temperatureOverride : httpProperties.getTemperature());
        body.put("options", options);

        List<ModelToolDefinition> resolvedTools = toolPayloadBuilder.resolveOllamaTools(
                request != null ? request.getTools() : null,
                request != null ? request.getToolChoice() : null);
        if (resolvedTools != null && !resolvedTools.isEmpty()) {
            body.put("tools", toolPayloadBuilder.buildOllamaTools(resolvedTools));
        }
        return body;
    }
}

