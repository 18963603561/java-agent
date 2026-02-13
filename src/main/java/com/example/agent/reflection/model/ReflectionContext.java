package com.example.agent.reflection.model;

/**
 * 反思上下文对象。
 *
 * <p>用途：统一承载反思输入契约，避免策略/解析层散落使用动态 Map 结构。</p>
 */
public class ReflectionContext {

    /**
     * 步骤类型。
     */
    private final String stepType;

    /**
     * 当前尝试次数。
     */
    private final Integer attempt;

    /**
     * 输出摘要对象。
     */
    private final ReflectionOutputSummary outputSummary;

    /**
     * 结构化结果映射。
     */
    private final java.util.Map<String, Object> result;

    /**
     * 输出指纹对象。
     */
    private final ReflectionOutputDigest outputDigest;

    private ReflectionContext(Builder builder) {
        this.stepType = builder.stepType;
        this.attempt = builder.attempt;
        this.outputSummary = builder.outputSummary;
        this.outputDigest = builder.outputDigest;
        this.result = builder.result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStepType() {
        return stepType;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public ReflectionOutputSummary getOutputSummary() {
        return outputSummary;
    }

    public ReflectionOutputDigest getOutputDigest() {
        return outputDigest;
    }

    public java.util.Map<String, Object> getResult() {
        return result;
    }

    /**
     * 反思上下文构建器。
     */
    public static class Builder {

        private String stepType;
        private Integer attempt;
        private ReflectionOutputSummary outputSummary;
        private ReflectionOutputDigest outputDigest;
        private java.util.Map<String, Object> result;

        public Builder stepType(String stepType) {
            this.stepType = stepType;
            return this;
        }

        public Builder attempt(Integer attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder outputSummary(ReflectionOutputSummary outputSummary) {
            this.outputSummary = outputSummary;
            return this;
        }

        public Builder outputDigest(ReflectionOutputDigest outputDigest) {
            this.outputDigest = outputDigest;
            return this;
        }

        public Builder result(java.util.Map<String, Object> result) {
            this.result = result;
            return this;
        }

        public ReflectionContext build() {
            return new ReflectionContext(this);
        }
    }
}
