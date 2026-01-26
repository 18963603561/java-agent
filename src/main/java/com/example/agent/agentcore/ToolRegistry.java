package com.example.agent.agentcore;

import org.springframework.stereotype.Component;

/**
 * 工具注册表，负责管理可用工具名称。
 */
@Component
public class ToolRegistry {

    /**
     * 解析工具名称。
     *
     * @param toolName 工具名称
     * @return 工具名称
     */
    public String resolve(String toolName) {
        return toolName;
    }
}
