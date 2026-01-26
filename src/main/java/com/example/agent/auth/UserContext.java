package com.example.agent.auth;

import java.util.List;

/**
 * 用户上下文，承载用户身份与角色信息。
 */
public class UserContext {

    public static final String CONTEXT_KEY = "userContext";

    /**
     * 用户标识。
     */
    private String userId;

    /**
     * 角色列表。
     */
    private List<String> roles;

    /**
     * 租户范围，可选。
     */
    private String tenantScope;

    public UserContext() {
    }

    public UserContext(String userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    public UserContext(String userId, List<String> roles, String tenantScope) {
        this.userId = userId;
        this.roles = roles;
        this.tenantScope = tenantScope;
    }

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
