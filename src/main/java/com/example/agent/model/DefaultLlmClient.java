package com.example.agent.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认模型客户端实现，基于路由与供应商调用。
 */
@Component
public class DefaultLlmClient implements LlmClient {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultLlmClient.class);

    /**
     * 模型路由器。
     */
    private final ModelRouter modelRouter;
    /**
     * 模型提供方。
     */
    private final ModelProvider modelProvider;

    /**
     * 构造模型客户端。
     *
     * @param modelRouter 模型路由器
     * @param modelProvider 模型提供方
     */
    public DefaultLlmClient(ModelRouter modelRouter, ModelProvider modelProvider) {
        this.modelRouter = modelRouter;
        this.modelProvider = modelProvider;
    }

    /**
     * 根据场景路由模型并发起调用。
     *
     * @param request 模型请求
     * @return 模型响应
     */
    @Override
    public ModelResponse generate(ModelRequest request) {
        ModelScene scene = request.getScene() != null ? request.getScene() : ModelScene.CHEAP;
        ModelDefinition definition = modelRouter.route(scene);
        log.info("模型调用, scene={}, modelId={}", scene, definition != null ? definition.getModelId() : null);
        return modelProvider.invoke(definition, request);
    }
}
