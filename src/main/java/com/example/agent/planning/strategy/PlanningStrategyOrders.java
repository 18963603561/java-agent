package com.example.agent.planning.strategy;

/**
 * 规划策略顺序常量。
 *
 * <p>用途：集中维护策略优先级，避免处理器中散落硬编码数字。
 */
public final class PlanningStrategyOrders {

    public static final int CHAIN_OF_THOUGHT = 10;
    public static final int THOUGHT_TREE = 20;
    public static final int MULTI_AGENT = 30;
    public static final int DEBATE = 40;
    public static final int RESEARCH = 50;
    public static final int REACT = 60;
    public static final int DIRECT_LLM = 70;
    public static final int TOOL_FALLBACK = 100;

    private PlanningStrategyOrders() {
    }
}

