package com.example.agent.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.agent.common.ErrorCodeException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 租户上下文过滤器，将租户信息写入 Reactor Context。
 */
@Component
public class TenantContextFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final TenantResolver tenantResolver;
    private final TenantProperties tenantProperties;

    public TenantContextFilter(TenantResolver tenantResolver, TenantProperties tenantProperties) {
        this.tenantResolver = tenantResolver;
        this.tenantProperties = tenantProperties;
    }

    /**
     * 过滤请求并注入租户上下文。
     *
     * @param exchange 请求上下文
     * @param chain 过滤器链
     * @return 处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isWhitelisted(exchange)) {
            return chain.filter(exchange);
        }
        TenantContext tenantContext = tenantResolver.resolve(exchange);
        if (tenantContext == null) {
            log.warn("租户标识缺失, path={}", exchange.getRequest().getPath());
            return Mono.error(new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "TENANT_MISSING", "租户标识缺失"));
        }
        exchange.getAttributes().put(TenantContext.CONTEXT_KEY, tenantContext);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(TenantContext.CONTEXT_KEY, tenantContext));
    }

    private boolean isWhitelisted(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        List<String> whitelist = tenantProperties.getWhitelistPaths();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.stream().anyMatch(path::equals);
    }
}
