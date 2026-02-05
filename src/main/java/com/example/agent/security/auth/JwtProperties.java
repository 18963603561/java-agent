package com.example.agent.security.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项。
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /**
     * 是否启用 JWT 鉴权。
     */
    private boolean enabled = false;

    /**
     * 是否校验签名。
     */
    private boolean verifySignature = false;

    /**
     * JWT 密钥。
     */
    private String secret;

    /**
     * 用户标识字段。
     */
    private String userClaim = "sub";

    /**
     * 角色字段。
     */
    private String rolesClaim = "roles";

    /**
     * 租户字段。
     */
    private String tenantClaim = "tenant";

    /**
     * 期望的签发方。
     */
    private String issuer;

    /**
     * 期望的受众。
     */
    private String audience;

    /**
     * 获取是否启用令牌鉴权。
     *
     * @return 是否启用令牌鉴权
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取是否校验签名。
     *
     * @return 是否校验签名
     */
    public boolean isVerifySignature() {
        return verifySignature;
    }

    public void setVerifySignature(boolean verifySignature) {
        this.verifySignature = verifySignature;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getUserClaim() {
        return userClaim;
    }

    public void setUserClaim(String userClaim) {
        this.userClaim = userClaim;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
