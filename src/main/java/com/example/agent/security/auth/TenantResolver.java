package com.example.agent.security.auth;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * 租户解析器，仅解析租户与请求追踪信息。
 */
@Component
public class TenantResolver {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 解析请求中的租户上下文。
     *
     * @param exchange 请求上下文
     * @return 租户上下文，若缺失租户则返回 null
     */
    public TenantContext resolve(ServerWebExchange exchange) {
        String tenantId = exchange.getRequest().getHeaders().getFirst(HEADER_TENANT_ID);
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        return new TenantContext(tenantId, null, Collections.emptyList(), requestId, traceId);
    }
}
