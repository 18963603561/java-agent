package com.example.agent.model;

import java.util.Collection;
import java.util.Collections;
import org.springframework.stereotype.Component;

/**
 * 模型注册表，提供模型配置的查询入口。
 */
@Component
public class ModelRegistry {

    private final ModelConfigProperties modelConfigProperties;

    public ModelRegistry(ModelConfigProperties modelConfigProperties) {
        this.modelConfigProperties = modelConfigProperties;
    }

    public ModelDefinition getModel(String modelId) {
        if (modelConfigProperties.getModels() == null) {
            return null;
        }
        return modelConfigProperties.getModels().get(modelId);
    }

    public Collection<ModelDefinition> listModels() {
        if (modelConfigProperties.getModels() == null) {
            return Collections.emptyList();
        }
        return modelConfigProperties.getModels().values();
    }
}
