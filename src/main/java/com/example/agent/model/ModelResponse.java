package com.example.agent.model;

/**
 * 模型响应。
 */
public class ModelResponse {

    /**
     * 模型标识。
     */
    private String modelId;
    /**
     * 输出内容。
     */
    private String content;
    /**
     * 输入令牌数量。
     */
    private int inputTokens;
    /**
     * 输出令牌数量。
     */
    private int outputTokens;
    /**
     * 原始输出引用键，用于追踪模型原始结果。
     */
    private String rawRef;

    /**
     * 空构造方法，便于序列化。
     */
    public ModelResponse() {
    }

    /**
     * 构造模型响应。
     *
     * @param modelId 模型标识
     * @param content 输出内容
     * @param inputTokens 输入令牌数量
     * @param outputTokens 输出令牌数量
     */
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

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }
}
