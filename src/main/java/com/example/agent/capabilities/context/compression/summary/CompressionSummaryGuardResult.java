package com.example.agent.capabilities.context.compression.summary;

/**
 * 压缩摘要治理结果。
 */
public class CompressionSummaryGuardResult {

    /**
     * 是否通过治理。
     */
    private boolean passed;

    /**
     * 治理后的摘要。
     */
    private String summary;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 脱敏次数。
     */
    private int redactedCount;

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getRedactedCount() {
        return redactedCount;
    }

    public void setRedactedCount(int redactedCount) {
        this.redactedCount = redactedCount;
    }
}

