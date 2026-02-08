package com.example.agent.planning.strategy;

/**
 * 规划策略处理器。
 */
public interface PlanningStrategyHandler {

    /**
     * 获取策略类型。
     *
     * @return 策略类型
     */
    PlanningStrategyType getType();

    /**
     * 获取执行优先级，值越小优先级越高。
     *
     * @return 优先级
     */
    int getOrder();

    /**
     * 判定当前上下文是否命中。
     *
     * @param context 策略上下文
     * @return 是否命中
     */
    boolean matches(PlanningStrategyContext context);

    /**
     * 执行策略并返回结果。
     *
     * @param context 策略上下文
     * @return 策略执行结果
     */
    PlanningStrategyResult handle(PlanningStrategyContext context);
}

