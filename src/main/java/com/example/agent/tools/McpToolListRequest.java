package com.example.agent.tools;

/**
 * MCP 工具列表请求。
 */
public class McpToolListRequest {

    private String serverId;
    private String cursor;
    private Integer size;

    public McpToolListRequest() {
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
