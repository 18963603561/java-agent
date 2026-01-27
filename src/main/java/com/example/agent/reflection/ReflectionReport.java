package com.example.agent.reflection;

/**
 * 反思报告，描述质量评估结果。
 */
public class ReflectionReport {

    /**
     * 质量评分，范围 0~1。
     */
    private double score;

    /**
     * 反思说明与改进建议。
     */
    private String notes;

    public ReflectionReport() {
    }

    public ReflectionReport(double score, String notes) {
        this.score = score;
        this.notes = notes;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
