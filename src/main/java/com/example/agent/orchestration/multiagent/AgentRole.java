package com.example.agent.orchestration.multiagent;

/**
 * 智能体角色定义。
 */
public class AgentRole {

    private String roleId;
    private String name;
    private String modelId;
    private String description;

    public AgentRole() {
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
