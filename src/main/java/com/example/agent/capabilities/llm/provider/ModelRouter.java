package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.config.ModelConfigProperties;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型路由器，根据场景选择模型。
 */
@Component
public class ModelRouter {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    /**
     * 模型配置集合。
     */
    private final ModelConfigProperties modelConfigProperties;
    /**
     * 模型注册表。
     */
    private final ModelRegistry modelRegistry;

    /**
     * 构造模型路由器。
     *
     * @param modelConfigProperties 模型配置集合
     * @param modelRegistry 模型注册表
     */
    public ModelRouter(ModelConfigProperties modelConfigProperties, ModelRegistry modelRegistry) {
        this.modelConfigProperties = modelConfigProperties;
        this.modelRegistry = modelRegistry;
    }

    /**
     * 根据场景路由模型。
     *
     * @param scene 场景
     * @return 模型定义
     */
    public ModelDefinition route(ModelScene scene) {
        if (scene == null) {
            log.warn("模型路由场景为空，返回空模型");
            return null;
        }
        String key = scene.name().toLowerCase(Locale.ROOT);
        String modelId = modelConfigProperties.getRoutes().get(key);
        if (!StringUtils.hasText(modelId) && modelConfigProperties.isFallbackEnabled()) {
            modelId = modelConfigProperties.getFallbackModelId();
        }
        ModelDefinition model = modelRegistry.getModel(modelId);
        if (model == null
                && modelConfigProperties.isFallbackEnabled()
                && modelConfigProperties.getFallbackModelId() != null) {
            model = modelRegistry.getModel(modelConfigProperties.getFallbackModelId());
        }
        if (model == null) {
            log.warn("模型路由未命中, scene={}, routeModelId={}, fallbackEnabled={}, fallbackModelId={}",
                    scene,
                    modelId,
                    modelConfigProperties.isFallbackEnabled(),
                    modelConfigProperties.getFallbackModelId());
        } else {
            log.debug("模型路由, scene={}, modelId={}", scene, model.getModelId());
        }
        return model;
    }
}
