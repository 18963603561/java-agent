package com.example.agent.model;

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

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelConfigProperties modelConfigProperties;
    private final ModelRegistry modelRegistry;

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
        String key = scene.name().toLowerCase(Locale.ROOT);
        String modelId = modelConfigProperties.getRoutes().get(key);
        if (!StringUtils.hasText(modelId)) {
            modelId = modelConfigProperties.getFallbackModelId();
        }
        ModelDefinition model = modelRegistry.getModel(modelId);
        if (model == null && modelConfigProperties.getFallbackModelId() != null) {
            model = modelRegistry.getModel(modelConfigProperties.getFallbackModelId());
        }
        log.debug("模型路由, scene={}, modelId={}", scene, model != null ? model.getModelId() : null);
        return model;
    }
}
