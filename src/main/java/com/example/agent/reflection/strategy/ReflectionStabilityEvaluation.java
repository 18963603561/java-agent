package com.example.agent.reflection.strategy;

import java.util.List;

/**
 * 反思稳定性评估结果。
 */
public class ReflectionStabilityEvaluation {

    /**
     * 稳定性评分。
     */
    private final double score;

    /**
     * 是否稳定。
     */
    private final boolean stable;

    /**
     * 告警列表。
     */
    private final List<String> warnings;

    public ReflectionStabilityEvaluation(double score, boolean stable, List<String> warnings) {
        this.score = score;
        this.stable = stable;
        this.warnings = warnings;
    }

    public double getScore() {
        return score;
    }

    public boolean isStable() {
        return stable;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
