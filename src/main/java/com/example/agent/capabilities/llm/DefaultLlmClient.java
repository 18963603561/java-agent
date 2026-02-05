package com.example.agent.capabilities.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认模型客户端实现，基于路由与供应商调用。
 * <p>用途：根据场景选择模型并转交给提供方执行。
 * <p>输入：模型请求对象。
 * <p>输出：模型响应对象。
 * <p>边界：请求未指定场景时使用默认场景。
 * <p>示例：
 * <pre>{@code
 * ModelResponse response = defaultLlmClient.generate(request);
 * }</pre>
 */
@Component
public class DefaultLlmClient implements LlmClient {

    /**
     * 日志记录器。
     * <p>示例：记录场景与模型标识。
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultLlmClient.class);

    /**
     * 模型路由器。
     * <p>示例：根据场景选择模型定义。
     */
    private final ModelRouter modelRouter;
    /**
     * 模型提供方。
     * <p>示例：调用 {@code ModelProvider.invoke} 获取结果。
     */
    private final ModelProvider modelProvider;

    /**
     * 构造模型客户端。
     *
     * <p>输入：模型路由器与模型提供方。
     * <p>输出：初始化后的客户端。
     * <p>示例：
     * <pre>{@code
     * new DefaultLlmClient(modelRouter, modelProvider);
     * }</pre>
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
     * <p>输入：模型请求对象。
     * <p>输出：模型响应对象。
     * <p>边界：场景为空时使用 {@code CHEAP} 场景。
     * <p>示例：
     * <pre>{@code
     * ModelResponse response = generate(request);
     * }</pre>
     *
     * @param request 模型请求
     * @return 模型响应
     */
    @Override
    public ModelResponse generate(ModelRequest request) {
        // 选择默认场景，避免空场景导致路由失败。
        ModelScene scene = request.getScene() != null ? request.getScene() : ModelScene.CHEAP;
        // 路由到目标模型定义。
        ModelDefinition definition = modelRouter.route(scene);
        // 记录调用日志，便于追踪模型路由结果。
        log.info("模型调用, scene={}, modelId={}", scene, definition != null ? definition.getModelId() : null);
        // 交由模型提供方执行实际调用。
        return modelProvider.invoke(definition, request);
    }
}
