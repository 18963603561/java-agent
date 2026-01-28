package com.example.agent.streaming;

/**
 * 上下文压缩摘要，用于事件载荷中的压缩统计。
 */
public class ContextCompressionSummary {

    /**
     * 触发原因。
     */
    private String triggerReason;

    /**
     * 压缩前 token 估算。
     */
    private Integer beforeTokens;

    /**
     * 压缩后 token 估算。
     */
    private Integer afterTokens;

    /**
     * 压缩耗时（毫秒）。
     */
    private Long durationMs;

    /**
     * 摘要版本号。
     */
    private String summaryVersion;

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public Integer getBeforeTokens() {
        return beforeTokens;
    }

    public void setBeforeTokens(Integer beforeTokens) {
        this.beforeTokens = beforeTokens;
    }

    public Integer getAfterTokens() {
        return afterTokens;
    }

    public void setAfterTokens(Integer afterTokens) {
        this.afterTokens = afterTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(String summaryVersion) {
        this.summaryVersion = summaryVersion;
    }
}
