package com.example.agent.capabilities.llm.provider;

/**
 * 模型定义，描述模型与供应商配置。
 */
public class ModelDefinition {

    /**
     * 模型标识。
     */
    private String modelId;
    /**
     * 模型供应商。
     */
    private String provider;
    /**
     * 模型服务地址。
     */
    private String endpoint;
    /**
     * 模型级接口密钥，优先级高于全局配置。
     */
    private String apiKey;
    /**
     * 输入成本（美元）。
     */
    private double inputCostUsd;
    /**
     * 输出成本（美元）。
     */
    private double outputCostUsd;
    /**
     * 最大令牌数。
     */
    private int maxTokens;
    /**
     * 是否支持 developer 角色，默认支持，可按模型配置关闭。
     */
    private Boolean supportsDeveloperRole;

    /**
     * 获取模型标识。
     *
     * @return 模型标识
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * 设置模型标识。
     *
     * @param modelId 模型标识
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    /**
     * 获取模型供应商。
     *
     * @return 模型供应商
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置模型供应商。
     *
     * @param provider 模型供应商
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取模型服务地址。
     *
     * @return 模型服务地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置模型服务地址。
     *
     * @param endpoint 模型服务地址
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 获取模型接口密钥。
     *
     * @return 模型接口密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置模型接口密钥。
     *
     * @param apiKey 模型接口密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取输入成本。
     *
     * @return 输入成本
     */
    public double getInputCostUsd() {
        return inputCostUsd;
    }

    /**
     * 设置输入成本。
     *
     * @param inputCostUsd 输入成本
     */
    public void setInputCostUsd(double inputCostUsd) {
        this.inputCostUsd = inputCostUsd;
    }

    /**
     * 获取输出成本。
     *
     * @return 输出成本
     */
    public double getOutputCostUsd() {
        return outputCostUsd;
    }

    /**
     * 设置输出成本。
     *
     * @param outputCostUsd 输出成本
     */
    public void setOutputCostUsd(double outputCostUsd) {
        this.outputCostUsd = outputCostUsd;
    }

    /**
     * 获取最大令牌数。
     *
     * @return 最大令牌数
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 设置最大令牌数。
     *
     * @param maxTokens 最大令牌数
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 获取是否支持 developer 角色。
     *
     * @return 是否支持 developer 角色
     */
    public Boolean getSupportsDeveloperRole() {
        return supportsDeveloperRole;
    }

    /**
     * 设置是否支持 developer 角色。
     *
     * @param supportsDeveloperRole 是否支持 developer 角色
     */
    public void setSupportsDeveloperRole(Boolean supportsDeveloperRole) {
        this.supportsDeveloperRole = supportsDeveloperRole;
    }
}
