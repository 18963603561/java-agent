package com.example.agent.context;

import java.time.Instant;
import java.util.List;

/**
 * 运行时元信息。
 */
public class RuntimeMeta {

    /**
     * 租户标识。
     */
    private String tenantId;

    /**
     * 用户标识。
     */
    private String userId;

    /**
     * 会话标识。
     */
    private String sessionId;

    /**
     * 工作流标识。
     */
    private String workflowId;

    /**
     * 请求标识。
     */
    private String requestId;

    /**
     * 链路追踪标识。
     */
    private String traceId;

    /**
     * 语言偏好。
     */
    private String locale;

    /**
     * 输出格式偏好。
     */
    private String outputFormat;

    /**
     * 令牌预算上限。
     */
    private Integer tokenBudget;

    /**
     * 允许使用的工具列表。
     */
    private List<String> allowedTools;

    /**
     * 请求时间。
     */
    private Instant requestTime;

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
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

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public Integer getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(Integer tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public List<String> getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools;
    }

    public Instant getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(Instant requestTime) {
        this.requestTime = requestTime;
    }
}