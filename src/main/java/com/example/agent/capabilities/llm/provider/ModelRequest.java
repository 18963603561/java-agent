package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.tooling.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolDefinition;

import java.util.List;

/**
 * 模型请求。
 */
public class ModelRequest {

    /**
     * 模型提示词内容。
     */
    private String prompt;
    /**
     * 模型场景。
     */
    private ModelScene scene;
    /**
     * 模型可用工具定义列表。
     */
    private List<ModelToolDefinition> tools;
    /**
     * 多角色消息列表。
     */
    private List<PromptMessage> messages;
    /**
     * 工具选择策略。
     */
    private ModelToolChoice toolChoice;
    /**
     * 温度覆盖值，空值表示沿用默认配置。
     */
    private Double temperature;

    /**
     * 空构造方法，便于序列化。
     */
    public ModelRequest() {
    }

    /**
     * 构造模型请求。
     *
     * @param prompt 提示内容
     * @param scene 模型场景
     */
    public ModelRequest(String prompt, ModelScene scene) {
        this.prompt = prompt;
        this.scene = scene;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public ModelScene getScene() {
        return scene;
    }

    public void setScene(ModelScene scene) {
        this.scene = scene;
    }

    public List<ModelToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ModelToolDefinition> tools) {
        this.tools = tools;
    }

    public List<PromptMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<PromptMessage> messages) {
        this.messages = messages;
    }

    public ModelToolChoice getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(ModelToolChoice toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}
