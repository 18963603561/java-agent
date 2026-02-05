package com.example.agent.security.auth;

import java.util.Collections;
import java.util.List;

/**
 * 租户上下文，承载租户与调用方的基础信息。
 */
public class TenantContext {

    public static final String CONTEXT_KEY = "tenantContext";

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 用户标识。
     */
    private String userId;

    /**
     * 用户角色列表。
     */
    private List<String> roles;

    /**
     * 请求标识。
     */
    private String requestId;

    /**
     * 链路追踪标识。
     */
    private String traceId;

    public TenantContext() {
    }

    public TenantContext(String tenantId, String userId, List<String> roles, String requestId, String traceId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.roles = roles;
        this.requestId = requestId;
        this.traceId = traceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 使用用户上下文更新用户身份与角色信息。
     *
     * @param userContext 用户上下文
     */
    public void applyUserContext(UserContext userContext) {
        if (userContext == null) {
            return;
        }
        this.userId = userContext.getUserId();
        this.roles = userContext.getRoles() != null ? userContext.getRoles() : Collections.emptyList();
    }
}
