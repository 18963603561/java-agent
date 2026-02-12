package com.example.agent.capabilities.context.compression.domain.policy;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;

/**
 * 压缩降级策略。
 */
public interface CompressionFallbackPolicy {

    /**
     * 判断是否允许降级。
     *
     * @param mode 当前模式
     * @param executionResult 执行结果
     * @return 是否允许降级
     */
    boolean shouldFallback(String mode, CompressionExecutionResult executionResult);
}


