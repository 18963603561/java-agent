package com.example.agent.tools;

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
    List<ToolSummary> listToolSummaries(ToolQuery query);

    /**
     * 查询工具输入结构。
     *
     * @param toolName 工具名称
     * @return 输入结构定义
     */
    Map<String, Object> getToolSchema(String toolName);
}
