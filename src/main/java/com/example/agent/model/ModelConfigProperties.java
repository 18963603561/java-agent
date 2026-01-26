package com.example.agent.model;

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

    private Map<String, ModelDefinition> models = new HashMap<>();
    private Map<String, String> routes = new HashMap<>();
    private boolean fallbackEnabled = true;
    private String fallbackModelId;

    public Map<String, ModelDefinition> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelDefinition> models) {
        this.models = models;
    }

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String getFallbackModelId() {
        return fallbackModelId;
    }

    public void setFallbackModelId(String fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }
}
