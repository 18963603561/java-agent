package com.example.agent.multiagent;

import java.util.List;

/**
 * 智能体档案配置。
 */
public class AgentProfile {

    private String agentId;
    private String modelId;
    private String prompt;
    private List<String> allowTools;
    private Integer budgetTokens;

    public AgentProfile() {
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getAllowTools() {
        return allowTools;
    }

    public void setAllowTools(List<String> allowTools) {
        this.allowTools = allowTools;
    }

    public Integer getBudgetTokens() {
        return budgetTokens;
    }

    public void setBudgetTokens(Integer budgetTokens) {
        this.budgetTokens = budgetTokens;
    }
}
