package com.example.agent.runtime.summary;

/**
 * 语义摘要场景决策结果。
 *
 * <p>用途：同时携带场景与来源，便于日志与观测输出。</p>
 */
public final class SemanticSummaryScenarioDecision {

    /**
     * 场景对象。
     */
    private final SemanticSummaryScenario scenario;

    /**
     * 场景来源。
     */
    private final SemanticSummaryScenarioSource source;

    private SemanticSummaryScenarioDecision(SemanticSummaryScenario scenario,
                                            SemanticSummaryScenarioSource source) {
        this.scenario = scenario;
        this.source = source;
    }

    /**
     * 构建场景决策。
     *
     * @param scenario 场景对象
     * @param source 场景来源
     * @return 场景决策
     */
    public static SemanticSummaryScenarioDecision of(SemanticSummaryScenario scenario,
                                                     SemanticSummaryScenarioSource source) {
        // 判断场景是否为空，空时使用默认场景。
        SemanticSummaryScenario safeScenario = scenario != null ? scenario : SemanticSummaryScenario.DEFAULT;
        // 判断来源是否为空，空时使用默认来源。
        SemanticSummaryScenarioSource safeSource = source != null ? source : SemanticSummaryScenarioSource.DEFAULT;
        // 构建场景决策对象并返回。
        return new SemanticSummaryScenarioDecision(safeScenario, safeSource);
    }

    /**
     * 构建默认场景决策。
     *
     * @return 默认场景决策
     */
    public static SemanticSummaryScenarioDecision defaultDecision() {
        // 返回默认场景决策。
        return new SemanticSummaryScenarioDecision(SemanticSummaryScenario.DEFAULT,
                SemanticSummaryScenarioSource.DEFAULT);
    }

    public SemanticSummaryScenario getScenario() {
        return scenario;
    }

    public SemanticSummaryScenarioSource getSource() {
        return source;
    }
}
