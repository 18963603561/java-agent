package com.example.agent.capabilities.context.compression.contract;

import com.example.agent.capabilities.memory.model.MemoryRecord;

/**
 * 压缩执行结果，统一封装外部压缩调用产物与耗时信息。
 */
public class CompressionExecutionResult {

    /**
     * 压缩结果记忆记录。
     */
    private MemoryRecord compressed;

    /**
     * 压缩耗时毫秒。
     */
    private long durationMs;

    /**
     * 是否执行成功。
     */
    private boolean success;

    /**
     * 压缩执行来源（rule/llm/hybrid）。
     */
    private String source;

    /**
     * 压缩失败原因编码。
     */
    private String failureReason;

    /**
     * 压缩前令牌数。
     */
    private Integer originalTokens;

    /**
     * 压缩后令牌数。
     */
    private Integer compressedTokens;

    /**
     * 是否发生降级。
     */
    private boolean fallbackApplied;

    /**
     * 重试次数。
     */
    private int retryCount;

    /**
     * 触发比例阈值。
     */
    private Double triggerRatio;

    /**
     * 目标比例阈值。
     */
    private Double targetRatio;

    /**
     * 实际压缩后占比。
     */
    private Double actualRatio;

    /**
     * 是否达成目标比例。
     */
    private Boolean targetMet;

    public MemoryRecord getCompressed() {
        return compressed;
    }

    public void setCompressed(MemoryRecord compressed) {
        this.compressed = compressed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getOriginalTokens() {
        return originalTokens;
    }

    public void setOriginalTokens(Integer originalTokens) {
        this.originalTokens = originalTokens;
    }

    public Integer getCompressedTokens() {
        return compressedTokens;
    }

    public void setCompressedTokens(Integer compressedTokens) {
        this.compressedTokens = compressedTokens;
    }

    public boolean isFallbackApplied() {
        return fallbackApplied;
    }

    public void setFallbackApplied(boolean fallbackApplied) {
        this.fallbackApplied = fallbackApplied;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Double getTriggerRatio() {
        return triggerRatio;
    }

    public void setTriggerRatio(Double triggerRatio) {
        this.triggerRatio = triggerRatio;
    }

    public Double getTargetRatio() {
        return targetRatio;
    }

    public void setTargetRatio(Double targetRatio) {
        this.targetRatio = targetRatio;
    }

    public Double getActualRatio() {
        return actualRatio;
    }

    public void setActualRatio(Double actualRatio) {
        this.actualRatio = actualRatio;
    }

    public Boolean getTargetMet() {
        return targetMet;
    }

    public void setTargetMet(Boolean targetMet) {
        this.targetMet = targetMet;
    }
}
