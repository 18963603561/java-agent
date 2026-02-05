package com.example.agent.capabilities.llm;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型配置项，包含模型列表与路由规则。
 */
@Component
@ConfigurationProperties(prefix = "agent.model")
public class ModelConfigProperties {

    /**
     * 模型配置映射。
     */
    private Map<String, ModelDefinition> models = new HashMap<>();
    /**
     * 场景路由配置。
     */
    private Map<String, String> routes = new HashMap<>();
    /**
     * 是否启用回退。
     */
    private boolean fallbackEnabled = true;
    /**
     * 回退模型标识。
     */
    private String fallbackModelId;

    /**
     * 获取模型配置映射。
     *
     * @return 模型配置映射
     */
    public Map<String, ModelDefinition> getModels() {
        return models;
    }

    /**
     * 设置模型配置映射。
     *
     * @param models 模型配置映射
     */
    public void setModels(Map<String, ModelDefinition> models) {
        this.models = models;
    }

    /**
     * 获取场景路由配置。
     *
     * @return 场景路由配置
     */
    public Map<String, String> getRoutes() {
        return routes;
    }

    /**
     * 设置场景路由配置。
     *
     * @param routes 场景路由配置
     */
    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    /**
     * 是否启用回退。
     *
     * @return 是否启用回退
     */
    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * 设置是否启用回退。
     *
     * @param fallbackEnabled 是否启用回退
     */
    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 获取回退模型标识。
     *
     * @return 回退模型标识
     */
    public String getFallbackModelId() {
        return fallbackModelId;
    }

    /**
     * 设置回退模型标识。
     *
     * @param fallbackModelId 回退模型标识
     */
    public void setFallbackModelId(String fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }
}
