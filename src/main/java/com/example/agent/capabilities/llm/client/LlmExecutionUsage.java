package com.example.agent.capabilities.llm.client;

/**
 * LLM 调用令牌消耗信息。
 *
 * <p>用途：统一承载输入输出令牌，避免跨层重复计算总量。
 * <p>输入：模型响应中的 input/output tokens。
 * <p>输出：统一结果协议中的 usage 字段。
 */
public class LlmExecutionUsage {

    /**
     * 输入令牌数。
     */
    private final int inputTokens;

    /**
     * 输出令牌数。
     */
    private final int outputTokens;

    /**
     * 总令牌数。
     */
    private final int totalTokens;

    /**
     * 构造令牌消耗对象。
     *
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     */
    public LlmExecutionUsage(int inputTokens, int outputTokens) {
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
        this.totalTokens = this.inputTokens + this.outputTokens;
    }

    /**
     * 基于模型响应令牌构造对象。
     *
     * @param inputTokens 输入令牌数
     * @param outputTokens 输出令牌数
     * @return 令牌消耗对象
     */
    public static LlmExecutionUsage of(int inputTokens, int outputTokens) {
        return new LlmExecutionUsage(inputTokens, outputTokens);
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}

