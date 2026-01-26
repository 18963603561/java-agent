package com.example.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认 LLM 客户端实现，基于模型路由与供应商调用。
 */
@Component
public class DefaultLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmClient.class);

    private final ModelRouter modelRouter;
    private final ModelProvider modelProvider;

    public DefaultLlmClient(ModelRouter modelRouter, ModelProvider modelProvider) {
        this.modelRouter = modelRouter;
        this.modelProvider = modelProvider;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        ModelScene scene = request.getScene() != null ? request.getScene() : ModelScene.CHEAP;
        ModelDefinition definition = modelRouter.route(scene);
        log.info("模型调用, scene={}, modelId={}", scene, definition != null ? definition.getModelId() : null);
        return modelProvider.invoke(definition, request.getPrompt());
    }
}
