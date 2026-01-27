package com.example.agent.auth;

import com.example.agent.common.ErrorCodeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * API Key 与 JWT 鉴权实现。
 */
@Service
public class ApiKeyAuthenticator implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticator.class);

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_ROLES = "X-Roles";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_TENANT_SCOPE = "X-Tenant-Scope";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyProperties apiKeyProperties;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticator(ApiKeyProperties apiKeyProperties,
                               JwtProperties jwtProperties,
                               ObjectMapper objectMapper) {
        this.apiKeyProperties = apiKeyProperties;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行鉴权并返回用户上下文。
     *
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 用户上下文
     */
    @Override
    public UserContext authenticate(String apiKey, ServerWebExchange exchange) {
        UserContext trustedContext = authenticateTrustedUpstream(exchange);
        if (trustedContext != null) {
            validateTenantScope(trustedContext.getTenantScope(), exchange, trustedContext.getUserId());
            return trustedContext;
        }

        String authorization = header(exchange, HEADER_AUTHORIZATION);
        if (jwtProperties.isEnabled() && StringUtils.hasText(authorization)
                && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            UserContext userContext = authenticateJwt(token, exchange);
            validateTenantScope(userContext.getTenantScope(), exchange, userContext.getUserId());
            return userContext;
        }

        return authenticateApiKey(apiKey, exchange);
    }

    private UserContext authenticateApiKey(String apiKey, ServerWebExchange exchange) {
        String resolvedKey = StringUtils.hasText(apiKey) ? apiKey : header(exchange, HEADER_API_KEY);
        if (!StringUtils.hasText(resolvedKey)) {
            throw unauthorized(exchange, "api_key_missing");
        }
        ApiKeyProperties.ApiKeyEntry entry = apiKeyProperties.getApiKeys().get(resolvedKey);
        if (entry == null) {
            throw unauthorized(exchange, "api_key_invalid");
        }
        if (!StringUtils.hasText(entry.getUserId())) {
            throw unauthorized(exchange, "api_key_user_missing");
        }
        UserContext userContext = new UserContext(entry.getUserId(),
                safeRoles(entry.getRoles()),
                entry.getTenantScope());
        validateTenantScope(userContext.getTenantScope(), exchange, userContext.getUserId());
        return userContext;
    }

    private UserContext authenticateTrustedUpstream(ServerWebExchange exchange) {
        ApiKeyProperties.TrustedUpstreamProperties trusted = apiKeyProperties.getTrustedUpstream();
        if (trusted == null || !trusted.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(trusted.getToken())) {
            log.warn("Trusted upstream enabled but token missing");
            return null;
        }
        String headerName = StringUtils.hasText(trusted.getTokenHeader())
                ? trusted.getTokenHeader()
                : "X-Trusted-Token";
        String token = header(exchange, headerName);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (!Objects.equals(token, trusted.getToken())) {
            throw unauthorized(exchange, "trusted_token_invalid");
        }
        String userId = header(exchange, HEADER_USER_ID);
        String rolesHeader = header(exchange, HEADER_ROLES);
        String tenantScope = header(exchange, HEADER_TENANT_SCOPE);
        List<String> roles = parseRoleHeader(rolesHeader);
        if (!StringUtils.hasText(userId)) {
            userId = "trusted-upstream";
        }
        return new UserContext(userId, roles, tenantScope);
    }

    private UserContext authenticateJwt(String token, ServerWebExchange exchange) {
        if (!StringUtils.hasText(token)) {
            throw unauthorized(exchange, "jwt_missing");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized(exchange, "jwt_parts_invalid");
        }
        Map<String, Object> header = parseSection(parts[0], "header", exchange);
        Map<String, Object> payload = parseSection(parts[1], "payload", exchange);
        String alg = asText(header.get("alg"));
        if (jwtProperties.isVerifySignature()) {
            verifySignature(alg, parts[0], parts[1], parts[2], exchange);
        }
        validateJwtClaims(payload, exchange);

        String userClaim = jwtProperties.getUserClaim();
        String userId = asText(payload.get(userClaim));
        if (!StringUtils.hasText(userId)) {
            throw unauthorized(exchange, "jwt_user_missing");
        }
        List<String> roles = parseRoleClaim(payload.get(jwtProperties.getRolesClaim()));
        String tenantScope = asText(payload.get(jwtProperties.getTenantClaim()));
        return new UserContext(userId, roles, tenantScope);
    }

    private void validateJwtClaims(Map<String, Object> payload, ServerWebExchange exchange) {
        long now = Instant.now().getEpochSecond();
        Long exp = asLong(payload.get("exp"));
        if (exp != null && exp < now) {
            throw unauthorized(exchange, "jwt_expired");
        }
        Long nbf = asLong(payload.get("nbf"));
        if (nbf != null && nbf > now) {
            throw unauthorized(exchange, "jwt_not_active");
        }
        if (StringUtils.hasText(jwtProperties.getIssuer())) {
            String issuer = asText(payload.get("iss"));
            if (!Objects.equals(jwtProperties.getIssuer(), issuer)) {
                throw unauthorized(exchange, "jwt_issuer_mismatch");
            }
        }
        if (StringUtils.hasText(jwtProperties.getAudience())) {
            if (!matchAudience(payload.get("aud"), jwtProperties.getAudience())) {
                throw unauthorized(exchange, "jwt_audience_mismatch");
            }
        }
    }

    /**
     * 签名校验仅支持 HS256/384/512。
     */
    private void verifySignature(String alg,
                                 String headerPart,
                                 String payloadPart,
                                 String signaturePart,
                                 ServerWebExchange exchange) {
        if (!StringUtils.hasText(jwtProperties.getSecret())) {
            throw unauthorized(exchange, "jwt_secret_missing");
        }
        String javaAlg = toMacAlgorithm(alg);
        if (!StringUtils.hasText(javaAlg)) {
            throw unauthorized(exchange, "jwt_alg_unsupported");
        }
        try {
            Mac mac = Mac.getInstance(javaAlg);
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), javaAlg));
            byte[] expected = mac.doFinal((headerPart + "." + payloadPart).getBytes(StandardCharsets.UTF_8));
            byte[] actual = decodeBase64Url(signaturePart, exchange);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw unauthorized(exchange, "jwt_signature_mismatch");
            }
        } catch (ErrorCodeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unauthorized(exchange, "jwt_signature_failed");
        }
    }

    private Map<String, Object> parseSection(String encoded, String name, ServerWebExchange exchange) {
        try {
            byte[] decoded = decodeBase64Url(encoded, exchange);
            return objectMapper.readValue(decoded, new TypeReference<Map<String, Object>>() {
            });
        } catch (ErrorCodeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unauthorized(exchange, "jwt_" + name + "_invalid");
        }
    }

    private byte[] decodeBase64Url(String value, ServerWebExchange exchange) {
        try {
            return Base64.getUrlDecoder().decode(padBase64(value));
        } catch (IllegalArgumentException ex) {
            throw unauthorized(exchange, "jwt_base64_invalid");
        }
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        int padding = 4 - remainder;
        return value + "=".repeat(padding);
    }

    private List<String> parseRoleHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>();
        for (String role : header.split(",")) {
            String trimmed = role.trim();
            if (StringUtils.hasText(trimmed)) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    private List<String> parseRoleClaim(Object claim) {
        if (claim == null) {
            return Collections.emptyList();
        }
        if (claim instanceof String text) {
            return parseRoleHeader(text);
        }
        if (claim instanceof Collection<?> collection) {
            List<String> roles = new ArrayList<>();
            for (Object item : collection) {
                String text = asText(item);
                if (StringUtils.hasText(text)) {
                    roles.add(text);
                }
            }
            return roles;
        }
        return Collections.emptyList();
    }

    private List<String> safeRoles(List<String> roles) {
        return roles != null ? new ArrayList<>(roles) : Collections.emptyList();
    }

    /**
     * 租户范围校验，避免跨租户访问。
     */
    private void validateTenantScope(String tenantScope, ServerWebExchange exchange, String userId) {
        if (!StringUtils.hasText(tenantScope)) {
            return;
        }
        String tenantId = resolveTenantId(exchange);
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        if (!matchTenantScope(tenantScope, tenantId)) {
            String traceId = resolveTraceId(exchange);
            String requestId = resolveRequestId(exchange);
            log.warn("AUTH_FORBIDDEN, tenantId={}, userId={}, traceId={}, requestId={}, scope={}",
                    tenantId, userId, traceId, requestId, tenantScope);
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "FORBIDDEN", "tenant_scope_forbidden");
        }
    }

    private boolean matchTenantScope(String scope, String tenantId) {
        for (String item : scope.split(",")) {
            String trimmed = item.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if ("*".equals(trimmed) || tenantId.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private String toMacAlgorithm(String alg) {
        if (!StringUtils.hasText(alg)) {
            return null;
        }
        return switch (alg) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> null;
        };
    }

    private boolean matchAudience(Object audClaim, String expected) {
        if (audClaim instanceof String text) {
            return expected.equals(text);
        }
        if (audClaim instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (expected.equals(asText(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String asText(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ErrorCodeException unauthorized(ServerWebExchange exchange, String reason) {
        String tenantId = resolveTenantId(exchange);
        String userId = resolveUserId(exchange);
        String traceId = resolveTraceId(exchange);
        String requestId = resolveRequestId(exchange);
        log.warn("AUTH_FAILED, tenantId={}, userId={}, traceId={}, requestId={}, reason={}",
                tenantId, userId, traceId, requestId, reason);
        return new ErrorCodeException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "auth_failed");
    }

    private String resolveTenantId(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context != null && StringUtils.hasText(context.getTenantId())) {
            return context.getTenantId();
        }
        return header(exchange, HEADER_TENANT_ID);
    }

    private String resolveUserId(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context != null && StringUtils.hasText(context.getUserId())) {
            return context.getUserId();
        }
        return header(exchange, HEADER_USER_ID);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        return header(exchange, HEADER_TRACE_ID);
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        return header(exchange, HEADER_REQUEST_ID);
    }

    private String header(ServerWebExchange exchange, String name) {
        if (exchange == null) {
            return null;
        }
        String value = exchange.getRequest().getHeaders().getFirst(name);
        return StringUtils.hasText(value) ? value : null;
    }
}
