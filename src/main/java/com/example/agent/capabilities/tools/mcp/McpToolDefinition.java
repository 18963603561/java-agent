package com.example.agent.capabilities.tools.mcp;

import com.example.agent.capabilities.tools.model.ToolDefinition;

/**
 * MCP 工具定义。
 */
public class McpToolDefinition extends ToolDefinition {

    public McpToolDefinition() {
    }

    public McpToolDefinition(String name,
                             String version,
                             String description,
                             java.util.Map<String, Object> inputSchema,
                             java.util.Map<String, Object> outputSchema,
                             java.util.List<String> tags) {
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
    public McpToolDefinition(String name,
                             String version,
                             String description,
                             java.util.Map<String, Object> inputSchema,
                             java.util.Map<String, Object> outputSchema,
                             java.util.List<String> tags,
                             String authScope,
                             java.util.List<String> supportedLocales) {
        super(name, version, description, inputSchema, outputSchema, tags, authScope, supportedLocales);
    }
}
