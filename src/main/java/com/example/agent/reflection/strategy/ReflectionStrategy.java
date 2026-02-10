package com.example.agent.reflection.strategy;

import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionProperties;

/**
 * 反思策略接口。
 *
 * <p>用途：统一模型反思、规则反思等不同策略实现的执行契约。</p>
 */
public interface ReflectionStrategy {

    /**
     * 执行反思策略。
     *
     * @param context 反思执行上下文
     * @return 反思决策
     */
    ReflectionDecision execute(ReflectionExecutionContext context);

    /**
     * 获取策略执行优先级。
     *
     * @return 优先级，数值越小越先执行
     */
    default int order() {
        return Integer.MAX_VALUE;
    }

    /**
     * 判断当前策略在给定配置下是否启用。
     *
     * @param reflectionProperties 反思配置
     * @return true 表示启用
     */
    default boolean isEnabled(ReflectionProperties reflectionProperties) {
        return true;
    }
}
