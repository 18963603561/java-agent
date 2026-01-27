package com.example.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

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
}
