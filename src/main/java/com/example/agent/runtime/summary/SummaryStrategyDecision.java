package com.example.agent.runtime.summary;

/**
 * 摘要策略决策结果。
 *
 * <p>用途：统一返回命中的摘要策略与来源。</p>
 */
public final class SummaryStrategyDecision {

    /**
     * 命中的摘要策略。
     */
    private final SummaryBuildStrategy strategy;

    /**
     * 策略来源。
     */
    private final SummaryStrategySource source;

    private SummaryStrategyDecision(SummaryBuildStrategy strategy, SummaryStrategySource source) {
        this.strategy = strategy;
        this.source = source;
    }

    /**
     * 构建策略决策。
     *
     * @param strategy 摘要策略
     * @param source 策略来源
     * @return 策略决策
     */
    public static SummaryStrategyDecision of(SummaryBuildStrategy strategy, SummaryStrategySource source) {
        // 判断策略是否为空，空时使用语义策略。
        SummaryBuildStrategy safeStrategy = strategy != null ? strategy : SummaryBuildStrategy.SEMANTIC;
        // 判断来源是否为空，空时使用默认来源。
        SummaryStrategySource safeSource = source != null ? source : SummaryStrategySource.DEFAULT;
        // 构建并返回策略决策对象。
        return new SummaryStrategyDecision(safeStrategy, safeSource);
    }

    /**
     * 构建默认策略决策。
     *
     * @return 默认策略决策
     */
    public static SummaryStrategyDecision defaultDecision() {
        // 返回默认语义策略决策。
        return new SummaryStrategyDecision(SummaryBuildStrategy.SEMANTIC, SummaryStrategySource.DEFAULT);
    }

    public SummaryBuildStrategy getStrategy() {
        return strategy;
    }

    public SummaryStrategySource getSource() {
        return source;
    }
}

