package com.example.agent.auth;

import com.example.agent.common.ErrorCodeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * API Key 鉴权实现，作为默认鉴权扩展点。
 */
@Component
public class ApiKeyAuthenticator implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticator.class);
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_ROLES = "X-Roles";

    private final ApiKeyProperties apiKeyProperties;

    public ApiKeyAuthenticator(ApiKeyProperties apiKeyProperties) {
        this.apiKeyProperties = apiKeyProperties;
    }

    /**
     * 鉴权并返回用户上下文。
     *
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 用户上下文
     */
    @Override
    public UserContext authenticate(String apiKey, ServerWebExchange exchange) {
        if (!StringUtils.hasText(apiKey)) {
            log.error("API Key 缺失, path={}", exchange.getRequest().getPath());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key 缺失");
        }
        ApiKeyProperties.ApiKeyEntry entry = apiKeyProperties.getApiKeys().get(apiKey);
        if (entry == null || !StringUtils.hasText(entry.getUserId())) {
            log.warn("API Key 无效, path={}", exchange.getRequest().getPath());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API Key 无效");
        }

        UserContext userContext = new UserContext(entry.getUserId(), normalizeRoles(entry.getRoles()),
                entry.getTenantScope());

        applyTrustedUpstreamIfPresent(exchange, userContext);

        exchange.getAttributes().put(UserContext.CONTEXT_KEY, userContext);
        log.info("鉴权成功, userId={}, path={}", userContext.getUserId(), exchange.getRequest().getPath());
        return userContext;
    }

    private void applyTrustedUpstreamIfPresent(ServerWebExchange exchange, UserContext userContext) {
        ApiKeyProperties.TrustedUpstreamProperties trusted = apiKeyProperties.getTrustedUpstream();
        if (trusted == null || !trusted.isEnabled()) {
            return;
        }
        String headerUserId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
        String headerRoles = exchange.getRequest().getHeaders().getFirst(HEADER_ROLES);
        if (!StringUtils.hasText(headerUserId) && !StringUtils.hasText(headerRoles)) {
            return;
        }
        validateTrustedUpstream(trusted, exchange);
        if (!StringUtils.hasText(headerUserId)) {
            log.warn("可信上游用户标识缺失, path={}", exchange.getRequest().getPath());
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "TRUSTED_UPSTREAM_INVALID", "可信上游用户标识缺失");
        }
        userContext.setUserId(headerUserId.trim());
        userContext.setRoles(parseRoles(headerRoles));
        log.info("可信上游头生效, userId={}, roles={}, path={}",
                userContext.getUserId(), userContext.getRoles(), exchange.getRequest().getPath());
    }

    private void validateTrustedUpstream(ApiKeyProperties.TrustedUpstreamProperties trusted,
                                         ServerWebExchange exchange) {
        String tokenHeader = StringUtils.hasText(trusted.getTokenHeader())
                ? trusted.getTokenHeader()
                : "X-Trusted-Token";
        String token = exchange.getRequest().getHeaders().getFirst(tokenHeader);
        if (!StringUtils.hasText(trusted.getToken())
                || !StringUtils.hasText(token)
                || !trusted.getToken().equals(token)) {
            log.warn("可信上游令牌校验失败, header={}, path={}",
                    tokenHeader, exchange.getRequest().getPath());
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "TRUSTED_UPSTREAM_INVALID", "可信上游令牌校验失败");
        }
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String role : roles) {
            if (StringUtils.hasText(role)) {
                normalized.add(role.trim());
            }
        }
        return normalized;
    }

    private List<String> parseRoles(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                roles.add(part.trim());
            }
        }
        return roles;
    }
}
