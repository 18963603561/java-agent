package com.example.agent.reasoning.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 推理策略注册表。
 *
 * <p>用途：按策略类型路由到具体推理实现，避免调用方感知具体类。
 */
@Component
public class ReasoningStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(ReasoningStrategyRegistry.class);

    private final List<ReasoningStrategy> strategies;

    /**
     * 构造策略注册表。
     *
     * @param strategies 策略列表
     */
    public ReasoningStrategyRegistry(List<ReasoningStrategy> strategies) {
        this.strategies = strategies == null ? List.of() : List.copyOf(strategies);
    }

    /**
     * 根据策略类型执行推理。
     *
     * @param request 推理请求
     * @return 推理结果
     */
    public ReasoningResult execute(ReasoningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("reasoning_request_missing");
        }
        String strategyType = request.getStrategyType();
        for (ReasoningStrategy strategy : strategies) {
            if (strategy != null && strategy.supports(strategyType)) {
                log.debug("推理策略路由命中, strategyType={}, strategy={}",
                        strategyType,
                        strategy.getClass().getSimpleName());
                return strategy.execute(request);
            }
        }
        throw new IllegalArgumentException("unsupported_reasoning_strategy:" + strategyType);
    }
}

