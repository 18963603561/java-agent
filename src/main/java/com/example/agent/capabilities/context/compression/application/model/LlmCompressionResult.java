package com.example.agent.capabilities.context.compression.application.model;

/**
 * LLM 压缩结果。
 *
 * <p>用途：承载 LLM 编排阶段的结构化结果，供执行适配器映射为统一执行结果。</p>
 */
public class LlmCompressionResult {

    /**
     * 是否成功。
     */
    private boolean success;

    /**
     * 压缩摘要。
     */
    private String summary;

    /**
     * 摘要版本。
     */
    private String summaryVersion;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 模型输入 token。
     */
    private Integer inputTokens;

    /**
     * 模型输出 token。
     */
    private Integer outputTokens;

    /**
     * 调用耗时毫秒。
     */
    private long durationMs;

    /**
     * 重试次数。
     */
    private int retryCount;

    /**
     * 是否发生降级。
     */
    private boolean fallbackApplied;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isFallbackApplied() {
        return fallbackApplied;
    }

    public void setFallbackApplied(boolean fallbackApplied) {
        this.fallbackApplied = fallbackApplied;
    }
}

