package com.example.agent.reflection.parser;

/**
 * 反思解析结果。
 *
 * <p>用途：承载模型反思 JSON 的解析结果。</p>
 */
public class ReflectionParsingResult {

    /**
     * 评分。
     */
    private final double score;

    /**
     * 是否建议重试。
     */
    private final boolean retry;

    /**
     * 备注说明。
     */
    private final String notes;

    public ReflectionParsingResult(double score, boolean retry, String notes) {
        this.score = score;
        this.retry = retry;
        this.notes = notes;
    }

    public double getScore() {
        return score;
    }

    public boolean isRetry() {
        return retry;
    }

    public String getNotes() {
        return notes;
    }
}

