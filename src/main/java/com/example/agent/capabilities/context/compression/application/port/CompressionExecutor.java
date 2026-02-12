package com.example.agent.capabilities.context.compression.application.port;

import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;

/**
 * 压缩执行端口。
 *
 * <p>用途：抽象具体压缩实现，支持按模式切换 rule/llm/hybrid 执行器。</p>
 */
public interface CompressionExecutor {

    /**
     * 返回执行器支持的压缩模式。
     *
     * @return 模式标识
     */
    String mode();

    /**
     * 执行压缩。
     *
     * @param command 压缩命令
     * @return 执行结果
     */
    CompressionExecutionResult execute(CompressionCommand command);
}

