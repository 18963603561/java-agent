package com.example.agent.model;

/**
 * 模型响应。
 */
public class ModelResponse {

    private String modelId;
    private String content;
    private int inputTokens;
    private int outputTokens;

    public ModelResponse() {
    }

    public ModelResponse(String modelId, String content, int inputTokens, int outputTokens) {
        this.modelId = modelId;
        this.content = content;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }
}
