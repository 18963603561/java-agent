package com.example.agent.capabilities.tools.mcp.strategy;

/**
 * MCP 调用策略。
 */
public enum McpCallStrategy {
    /**
     * 仅远端调用。
     */
    REMOTE_ONLY,
    /**
     * 仅本地调用。
     */
    LOCAL_ONLY,
    /**
     * 远端优先。
     */
    REMOTE_FIRST,
    /**
     * 本地优先。
     */
    LOCAL_FIRST
}

