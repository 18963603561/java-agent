package com.example.agent.reflection;

/**
 * 反思结果，包含是否需要重试与报告内容。
 */
public class ReflectionResult {

    /**
     * 是否建议重试。
     */
    private boolean retryRequested;

    /**
     * 反思报告。
     */
    private ReflectionReport report;

    public ReflectionResult() {
    }

    public ReflectionResult(boolean retryRequested, ReflectionReport report) {
        this.retryRequested = retryRequested;
        this.report = report;
    }

    public boolean isRetryRequested() {
        return retryRequested;
    }

    public void setRetryRequested(boolean retryRequested) {
        this.retryRequested = retryRequested;
    }

    public ReflectionReport getReport() {
        return report;
    }

    public void setReport(ReflectionReport report) {
        this.report = report;
    }
}
