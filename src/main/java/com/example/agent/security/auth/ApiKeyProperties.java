package com.example.agent.security.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API Key 配置项，定义允许的 API Key 与可信上游策略。
 */
@Component
@ConfigurationProperties(prefix = "auth")
public class ApiKeyProperties {

    /**
     * 允许的 API Key 映射，Key 为 API Key，值为用户配置。
     */
    private Map<String, ApiKeyEntry> apiKeys = new HashMap<>();

    /**
     * 可信上游头模式配置。
     */
    private TrustedUpstreamProperties trustedUpstream = new TrustedUpstreamProperties();

    public Map<String, ApiKeyEntry> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Map<String, ApiKeyEntry> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public TrustedUpstreamProperties getTrustedUpstream() {
        return trustedUpstream;
    }

    public void setTrustedUpstream(TrustedUpstreamProperties trustedUpstream) {
        this.trustedUpstream = trustedUpstream;
    }

    /**
     * API Key 对应的用户配置。
     */
    public static class ApiKeyEntry {

        /**
         * 用户标识。
         */
        private String userId;

        /**
         * 角色列表。
         */
        private List<String> roles = new ArrayList<>();

        /**
         * 租户范围，可选。
         */
        private String tenantScope;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public String getTenantScope() {
            return tenantScope;
        }

        public void setTenantScope(String tenantScope) {
            this.tenantScope = tenantScope;
        }
    }

    /**
     * 可信上游头配置项。
     */
    public static class TrustedUpstreamProperties {

        /**
         * 是否启用可信上游头模式。
         */
        private boolean enabled;

        /**
         * 可信上游校验令牌。
         */
        private String token;

        /**
         * 可信上游令牌头名称。
         */
        private String tokenHeader = "X-Trusted-Token";

        /**
         * 获取可信上游模式是否启用。
         *
         * @return 是否启用可信上游模式
         */
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }
    }
}
