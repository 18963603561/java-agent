package com.example.agent.capabilities.llm;

import java.util.Collection;
import java.util.Collections;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 模型注册表，提供模型配置的查询入口。
 */
@Component
public class ModelRegistry {

    /**
     * 模型配置集合。
     */
    private final ModelConfigProperties modelConfigProperties;

    /**
     * 构造模型注册表。
     *
     * @param modelConfigProperties 模型配置集合
     */
    public ModelRegistry(ModelConfigProperties modelConfigProperties) {
        this.modelConfigProperties = modelConfigProperties;
    }

    /**
     * 按标识获取模型定义。
     *
     * @param modelId 模型标识
     * @return 模型定义，未找到则返回空
     */
    public ModelDefinition getModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        if (modelConfigProperties.getModels() == null) {
            return null;
        }
        return modelConfigProperties.getModels().get(modelId);
    }

    /**
     * 列出全部模型定义。
     *
     * @return 模型定义集合
     */
    public Collection<ModelDefinition> listModels() {
        if (modelConfigProperties.getModels() == null) {
            return Collections.emptyList();
        }
        return modelConfigProperties.getModels().values();
    }
}
