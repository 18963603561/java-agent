package com.example.agent.reflection;

/**
 * 反思报告，描述质量评估结果。
 */
public record ReflectionReport(double score, String notes) {

    /**
     * 反思报告。
     *
     * @param score 质量评分，范围 0~1
     * @param notes 反思说明与改进建议
     */
    public ReflectionReport {
    }
}
