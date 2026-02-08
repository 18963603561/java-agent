package com.example.agent.capabilities.tools;

import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * 工具目录服务接口。
 */
public interface ToolCatalogService {

    /**
     * 查询工具摘要列表。
     *
     * @param query 查询条件
     * @return 工具摘要列表
     */
    List<ToolSummary> listSummaries(ToolQuery query);

    /**
     * 按名称获取工具定义。
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    McpToolDefinition getDefinition(String toolName);

    /**
     * 查询工具输入结构。
     *
     * @param toolName 工具名称
     * @return 输入结构定义
     */
    Map<String, Object> getToolSchema(String toolName);
}
