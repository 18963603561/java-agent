package com.example.agent.reflection;

/**
 * 反思结果，包含是否需要重试与报告内容。
 */
public record ReflectionResult(boolean retryRequested, ReflectionReport report) {

    /**
     * 反思结果。
     *
     * @param retryRequested 是否建议重试
     * @param report 反思报告
     */
    public ReflectionResult {
    }
}
