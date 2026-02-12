package com.example.agent.capabilities.context.compression.domain.model;

import com.example.agent.capabilities.context.compression.contract.CompressionExecutionResult;

/**
 * 压缩结果领域对象。
 *
 * <p>用途：作为压缩域统一输出对象，隔离执行层结果与上游返回对象。</p>
 */
public class CompressionOutcome {

    /**
     * 执行结果。
     */
    private CompressionExecutionResult executionResult;

    public CompressionExecutionResult getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(CompressionExecutionResult executionResult) {
        this.executionResult = executionResult;
    }
}


