package com.example.agent.orchestration.multiagent;

/**
 * 智能体配置快照，运行期使用。
 */
public class AgentConfig {

    private String agentId;
    private String modelId;
    private String prompt;

    public AgentConfig() {
    }

    public AgentConfig(String agentId, String modelId, String prompt) {
        this.agentId = agentId;
        this.modelId = modelId;
        this.prompt = prompt;
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
}
