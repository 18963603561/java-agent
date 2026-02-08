package com.example.agent.planning.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规划策略注册中心。
 */
@Component
public class PlanningStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlanningStrategyRegistry.class);

    private final List<PlanningStrategyHandler> handlers;

    /**
     * 构造策略注册中心。
     *
     * @param handlers 策略处理器列表
     */
    public PlanningStrategyRegistry(List<PlanningStrategyHandler> handlers) {
        List<PlanningStrategyHandler> sorted = handlers != null ? new ArrayList<>(handlers) : new ArrayList<>();
        validateOrderUniqueness(sorted);
        sorted.sort(Comparator.comparingInt(PlanningStrategyHandler::getOrder));
        this.handlers = List.copyOf(sorted);
    }

    /**
     * 执行策略链路。
     *
     * @param context 策略上下文
     * @return 执行结果
     */
    public PlanningStrategyResult execute(PlanningStrategyContext context) {
        if (context == null) {
            return PlanningStrategyResult.empty();
        }
        log.info("策略调度开始, planId={}, handlers={}", context.getPlanId(), handlers.size());
        PlanningStrategyContext currentContext = context;
        PlanningStrategyResult lastResult = PlanningStrategyResult.empty();
        for (PlanningStrategyHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            if (!handler.matches(currentContext)) {
                log.debug("策略未命中, type={}, order={}, planId={}",
                        handler.getType(),
                        handler.getOrder(),
                        currentContext.getPlanId());
                continue;
            }
            log.debug("策略命中, type={}, order={}, planId={}",
                    handler.getType(),
                    handler.getOrder(),
                    currentContext.getPlanId());
            PlanningStrategyResult result;
            try {
                result = handler.handle(currentContext);
            } catch (Exception ex) {
                log.error("策略执行失败, type={}, order={}, planId={}, reason={}",
                        handler.getType(),
                        handler.getOrder(),
                        currentContext.getPlanId(),
                        ex.getMessage(),
                        ex);
                throw ex;
            }
            if (result == null) {
                continue;
            }
            lastResult = result;
            currentContext = currentContext.withPreviousStepKey(result.getNextStepKey());
            if (result.isTerminal()) {
                log.info("策略调度结束(终止), planId={}, type={}",
                        context.getPlanId(),
                        handler.getType());
                return result;
            }
        }
        log.info("策略调度结束(无终止), planId={}", context.getPlanId());
        return lastResult;
    }

    private void validateOrderUniqueness(List<PlanningStrategyHandler> sortedHandlers) {
        Map<Integer, PlanningStrategyType> orderMap = new HashMap<>();
        for (PlanningStrategyHandler handler : sortedHandlers) {
            if (handler == null) {
                continue;
            }
            int order = handler.getOrder();
            PlanningStrategyType previousType = orderMap.putIfAbsent(order, handler.getType());
            if (previousType != null) {
                log.error("策略优先级冲突, order={}, left={}, right={}",
                        order,
                        previousType,
                        handler.getType());
                throw new IllegalStateException("planning_strategy_order_conflict");
            }
        }
    }
}
