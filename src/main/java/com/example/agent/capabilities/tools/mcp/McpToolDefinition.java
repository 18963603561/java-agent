package com.example.agent.capabilities.tools.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具定义。
 */
public class McpToolDefinition {

    /**
     * 工具名称。
     */
    private String name;
    /**
     * 工具版本。
     */
    private String version;
    /**
     * 工具描述。
     */
    private String description;
    /**
     * 输入参数结构。
     */
    private Map<String, Object> inputSchema;
    /**
     * 输出结构。
     */
    private Map<String, Object> outputSchema;
    /**
     * 工具标签。
     */
    private List<String> tags;
    /**
     * 工具授权范围。
     */
    private String authScope;
    /**
     * 工具支持的语言区域列表。
     */
    private List<String> supportedLocales;

    public McpToolDefinition() {
    }

    public McpToolDefinition(String name, String version, String description,
                             Map<String, Object> inputSchema,
                             Map<String, Object> outputSchema,
                             List<String> tags) {
        this(name, version, description, inputSchema, outputSchema, tags, null, null);
    }

    /**
     * 构造 MCP 工具定义。
     *
     * @param name 工具名称
     * @param version 工具版本
     * @param description 工具描述
     * @param inputSchema 输入结构
     * @param outputSchema 输出结构
     * @param tags 标签列表
     * @param authScope 授权范围
     * @param supportedLocales 支持语言区域列表
     */
    public McpToolDefinition(String name, String version, String description,
                             Map<String, Object> inputSchema,
                             Map<String, Object> outputSchema,
                             List<String> tags,
                             String authScope,
                             List<String> supportedLocales) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.tags = tags;
        this.authScope = authScope;
        this.supportedLocales = supportedLocales;
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

    public String getAuthScope() {
        return authScope;
    }

    public void setAuthScope(String authScope) {
        this.authScope = authScope;
    }

    public List<String> getSupportedLocales() {
        return supportedLocales;
    }

    public void setSupportedLocales(List<String> supportedLocales) {
        this.supportedLocales = supportedLocales;
    }
}
