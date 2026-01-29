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
     * ?????
     */
    private List<String> tags;
    /**
     * ?????
     */
    private String costLevel;
    /**
     * ?????
     */
    private String latencyLevel;
    /**
     * ?????
     */
    private String authScope;

    /**
     * 空构造方法，便于序列化。
     */
    public ModelToolDefinition() {
    }

    /**
     * 构造工具定义。
     *
     * @param name 工具名称
     * @param description 工具描述
     * @param parameters 工具参数定义
     */
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
