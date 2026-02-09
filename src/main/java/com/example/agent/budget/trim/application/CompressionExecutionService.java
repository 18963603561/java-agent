package com.example.agent.budget.trim.application;

import com.example.agent.budget.trim.model.CompressionExecutionResult;
import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.security.auth.TenantContext;

/**
 * 压缩执行服务，负责调用外部压缩能力并返回统一执行结果。
 */
public interface CompressionExecutionService {

    /**
     * 执行上下文压缩。
     *
     * @param request 压缩请求
     * @param tenantContext 租户上下文
     * @return 执行结果
     */
    CompressionExecutionResult execute(ContextCompressionRequest request, TenantContext tenantContext);
}
