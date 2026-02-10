package com.example.agent.reasoning.common;

/**
 * 推理策略统一契约。
 *
 * <p>用途：为不同推理实现提供统一执行入口，便于扩展与路由。
 */
public interface ReasoningStrategy {

    /**
     * 判断是否支持指定策略类型。
     *
     * @param strategyType 策略类型
     * @return 是否支持
     */
    boolean supports(String strategyType);

    /**
     * 执行推理。
     *
     * @param request 推理请求
     * @return 推理结果
     */
    ReasoningResult execute(ReasoningRequest request);
}

