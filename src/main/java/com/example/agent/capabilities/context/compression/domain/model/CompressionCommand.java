package com.example.agent.capabilities.context.compression.domain.model;

import com.example.agent.budget.trim.model.ContextCompressionRequest;
import com.example.agent.security.auth.TenantContext;

/**
 * 压缩命令。
 *
 * <p>用途：作为压缩域统一输入对象，隔离上游请求与执行端口。</p>
 */
public class CompressionCommand {

    /**
     * 原始压缩请求。
     */
    private ContextCompressionRequest request;

    /**
     * 租户上下文。
     */
    private TenantContext tenantContext;

    public ContextCompressionRequest getRequest() {
        return request;
    }

    public void setRequest(ContextCompressionRequest request) {
        this.request = request;
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public void setTenantContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }
}

