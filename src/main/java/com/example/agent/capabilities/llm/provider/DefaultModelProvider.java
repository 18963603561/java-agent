package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认模型提供商协调器。
 *
 * <p>用途：根据模型定义路由到具体提供商适配器，未命中时走本地兜底策略。</p>
 */
@Component
public class DefaultModelProvider implements ModelProvider {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultModelProvider.class);

    /**
     * 提供商路由器。
     */
    private final ProviderRouter providerRouter;

    /**
     * 本地兜底策略。
     */
    private final LocalFallbackStrategy localFallbackStrategy;

    /**
     * 请求体构造器（保留公开方法兼容测试与调用方）。
     */
    private final ModelRequestBodyBuilder requestBodyBuilder;

    /**
     * 构造协调器。
     *
     * @param providerRouter 提供商路由器
     * @param localFallbackStrategy 本地兜底策略
     * @param requestBodyBuilder 请求体构造器
     */
    public DefaultModelProvider(ProviderRouter providerRouter,
                                LocalFallbackStrategy localFallbackStrategy,
                                ModelRequestBodyBuilder requestBodyBuilder) {
        this.providerRouter = providerRouter;
        this.localFallbackStrategy = localFallbackStrategy;
        this.requestBodyBuilder = requestBodyBuilder;
    }

    /**
     * 执行模型调用。
     *
     * @param definition 模型定义
     * @param request 模型请求
     * @return 模型响应
     */
    @Override
    public ModelResponse invoke(ModelDefinition definition, ModelRequest request) {
        ModelRequest safeRequest = request != null ? request : new ModelRequest();
        if (providerRouter != null) {
            var adapter = providerRouter.route(definition);
            if (adapter != null) {
                return adapter.invoke(definition, safeRequest);
            }
        }
        return invokeLocal(definition, safeRequest);
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
        return requestBodyBuilder.buildOpenAiRequestBody(modelId, request, definition);
    }

    /**
     * 构建原生接口请求体。
     *
     * @param modelId 模型标识
     * @param request 模型请求
     * @return 请求体
     */
    public Map<String, Object> buildOllamaRequestBody(String modelId, ModelRequest request) {
        return requestBodyBuilder.buildOllamaRequestBody(modelId, request);
    }

    /**
     * 本地兜底调用。
     *
     * @param definition 模型定义
     * @param request 模型请求
     * @return 模型响应
     */
    private ModelResponse invokeLocal(ModelDefinition definition, ModelRequest request) {
        String prompt = resolvePrompt(request);
        String modelId = definition != null ? definition.getModelId() : "local";
        int inputTokens = prompt != null ? prompt.length() : 0;
        String content = localFallbackStrategy.buildResponse(request);
        int outputTokens = content.length();
        log.info("本地兜底调用完成, modelId={}, inputTokens={}, outputTokens={}", modelId, inputTokens, outputTokens);
        return new ModelResponse(modelId, content, inputTokens, outputTokens);
    }

    /**
     * 解析请求中的提示词文本。
     *
     * @param request 模型请求
     * @return 提示词文本
     */
    private String resolvePrompt(ModelRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getPrompt() != null) {
            return request.getPrompt();
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (PromptMessage message : request.getMessages()) {
            if (message == null) {
                continue;
            }
            builder.append(message.getRole() != null ? message.getRole().name() : "USER");
            builder.append(":");
            builder.append(message.getContent() == null ? "" : message.getContent());
            builder.append("\n");
        }
        return builder.toString().trim();
    }
}
