package com.example.agent.capabilities.tools.hook;

import java.util.Map;

/**
 * Hook 执行上下文。
 */
public class HookContext {

    private HookType hookType;
    private String toolName;
    private String stepId;
    private String tenantId;
    private Map<String, Object> payload;

    public HookContext() {
    }

    public HookContext(HookType hookType, String toolName, String stepId, String tenantId,
                       Map<String, Object> payload) {
        this.hookType = hookType;
        this.toolName = toolName;
        this.stepId = stepId;
        this.tenantId = tenantId;
        this.payload = payload;
    }

    public HookType getHookType() {
        return hookType;
    }

    public void setHookType(HookType hookType) {
        this.hookType = hookType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
