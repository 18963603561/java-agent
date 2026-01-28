package com.example.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 模型侧工具定义，描述函数调用式工具结构。
 */
public class ModelToolDefinition {

    /**
     * 工具名称。
     */
    private String name;
    /**
     * 工具描述。
     */
    private String description;
    /**
     * 工具参数定义，直接承载 inputSchema。
     */
    private JsonNode parameters;
    /**
     * 宸ュ叿鏍囩銆?     */
    private List<String> tags;
    /**
     * 鎴愭湰绛夌骇鎻愮ず銆?     */
    private String costLevel;
    /**
     * 鏃跺欢绛夌骇鎻愮ず銆?     */
    private String latencyLevel;
    /**
     * 鎺堟潈鑼冨洿鎻愮ず銆?     */
    private String authScope;

    public ModelToolDefinition() {
    }

    public ModelToolDefinition(String name, String description, JsonNode parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCostLevel() {
        return costLevel;
    }

    public void setCostLevel(String costLevel) {
        this.costLevel = costLevel;
    }

    public String getLatencyLevel() {
        return latencyLevel;
    }

    public void setLatencyLevel(String latencyLevel) {
        this.latencyLevel = latencyLevel;
    }

    public String getAuthScope() {
        return authScope;
    }

    public void setAuthScope(String authScope) {
        this.authScope = authScope;
    }
}
