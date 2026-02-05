package com.example.agent.capabilities.tools;

import java.util.List;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;

/**
 * 工具目录接口。
 */
public interface ToolCatalog {

    /**
     * 查询工具摘要列表。
     *
     * @param query 查询条件
     * @return 摘要列表
     */
    List<ToolSummary> listSummaries(ToolQuery query);

    /**
     * 按名称获取工具定义。
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    McpToolDefinition getDefinition(String toolName);
}