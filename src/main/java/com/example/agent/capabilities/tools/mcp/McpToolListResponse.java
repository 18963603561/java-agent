package com.example.agent.capabilities.tools.mcp;

import com.example.agent.capabilities.tools.model.ToolDefinition;
import java.util.List;

/**
 * MCP 工具列表响应。
 */
public class McpToolListResponse {

    private List<ToolDefinition> tools;
    private String nextCursor;
    private boolean hasMore;

    public McpToolListResponse() {
    }

    public McpToolListResponse(List<ToolDefinition> tools, String nextCursor, boolean hasMore) {
        this.tools = tools;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
