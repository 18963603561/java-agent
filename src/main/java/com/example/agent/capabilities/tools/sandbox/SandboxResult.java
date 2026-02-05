package com.example.agent.capabilities.tools.sandbox;

import java.util.Map;

/**
 * 沙箱执行结果。
 */
public class SandboxResult {

    private String status;
    private Map<String, Object> output;
    private Map<String, Object> error;

    public SandboxResult() {
    }

    public SandboxResult(String status, Map<String, Object> output, Map<String, Object> error) {
        this.status = status;
        this.output = output;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public Map<String, Object> getError() {
        return error;
    }

    public void setError(Map<String, Object> error) {
        this.error = error;
    }
}
