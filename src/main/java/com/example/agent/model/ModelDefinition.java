package com.example.agent.model;

/**
 * 模型定义，描述模型与供应商配置。
 */
public class ModelDefinition {

    private String modelId;
    private String provider;
    private String endpoint;
    private double inputCostUsd;
    private double outputCostUsd;
    private int maxTokens;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public double getInputCostUsd() {
        return inputCostUsd;
    }

    public void setInputCostUsd(double inputCostUsd) {
        this.inputCostUsd = inputCostUsd;
    }

    public double getOutputCostUsd() {
        return outputCostUsd;
    }

    public void setOutputCostUsd(double outputCostUsd) {
        this.outputCostUsd = outputCostUsd;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
