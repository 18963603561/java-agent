package com.example.agent.capabilities.tools.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具定义。
 */
public class McpToolDefinition {

    private String name;
    private String version;
    private String description;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private List<String> tags;

    public McpToolDefinition() {
    }

    public McpToolDefinition(String name, String version, String description,
                             Map<String, Object> inputSchema,
                             Map<String, Object> outputSchema,
                             List<String> tags) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
