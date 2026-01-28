package com.example.agent.tools;

import java.util.List;

/**
 * 工具摘要信息。
 */
public class ToolSummary {

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 工具描述。
     */
    private String description;

    /**
     * 标签列表。
     */
    private List<String> tags;

    /**
     * 成本等级。
     */
    private String costLevel;

    /**
     * 时延等级。
     */
    private String latencyLevel;

    /**
     * 授权范围。
     */
    private String authScope;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCostLevel() {
        return costLevel;
    }

    public void setCostLevel(String costLevel) {
        this.costLevel = costLevel;
    }

    public String getLatencyLevel() {
        return latencyLevel;
    }

    public void setLatencyLevel(String latencyLevel) {
        this.latencyLevel = latencyLevel;
    }

    public String getAuthScope() {
        return authScope;
    }

    public void setAuthScope(String authScope) {
        this.authScope = authScope;
    }
}