package com.example.agent.sandbox;

import java.util.Map;

/**
 * 沙箱执行请求。
 */
public class SandboxRequest {

    private String toolName;
    private Map<String, Object> input;
    private Map<String, Object> limits;

    public SandboxRequest() {
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getLimits() {
        return limits;
    }

    public void setLimits(Map<String, Object> limits) {
        this.limits = limits;
    }
}
