package com.example.agent.capabilities.context.compression.application;

import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.capabilities.context.compression.domain.model.CompressionCommand;
import com.example.agent.capabilities.context.compression.domain.model.CompressionOutcome;
import com.example.agent.security.auth.TenantContext;
import org.springframework.stereotype.Component;

/**
 * 压缩模型映射器。
 *
 * <p>用途：负责压缩上下游对象转换，避免调用方直接依赖执行域模型。</p>
 */
@Component
public class CompressionModelMapper {

    /**
     * 构建压缩命令对象。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 压缩命令
     */
    public CompressionCommand toCommand(ContextCompressionRequest request, TenantContext tenantContext) {
        CompressionCommand command = new CompressionCommand();
        command.setRequest(request);
        command.setTenantContext(tenantContext);
        return command;
    }

    /**
     * 构建压缩领域结果对象。
     *
     * @param executionResult 执行结果
     * @return 压缩领域结果
     */
    public CompressionOutcome toOutcome(CompressionExecutionResult executionResult) {
        CompressionOutcome outcome = new CompressionOutcome();
        outcome.setExecutionResult(executionResult);
        return outcome;
    }
}

