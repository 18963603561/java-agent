package com.example.agent.capabilities.tools.skill;

/**
 * 技能路由定义，描述路由到具体工具的映射。
 */
public class SkillRoute {

    private String toolName;
    private String modelId;

    public SkillRoute() {
    }

    public SkillRoute(String toolName, String modelId) {
        this.toolName = toolName;
        this.modelId = modelId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
}
